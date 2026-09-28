import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteDocument, getDocuments, reindexDocument, uploadDocument, type DocumentItem } from './authApi'
import { isAbortError } from './apiClient'
import './knowledge.css'

type IconName = 'upload' | 'file' | 'search' | 'arrow' | 'refresh' | 'trash' | 'check' | 'book'
function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, string> = { upload: 'M12 16V3m-5 5 5-5 5 5M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5', file: 'M14 2H5v20h14V7l-5-5Zm0 0v6h5M8 12h8m-8 4h6', search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0', arrow: 'M4 12h16m-6-6 6 6-6 6', refresh: 'M20 7a9 9 0 1 0 1 8M20 2v6h-6', trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7', check: 'm5 12 4 4L19 6', book: 'M12 5v16M3 3c4-1 7 0 9 2 2-2 5-3 9-2v16c-4-1-7 0-9 2-2-2-5-3-9-2V3Z' }
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>
}

const statusLabels: Record<string, string> = { READY: '可用于问答', INDEXED: '可用于问答', UPLOADED: '待处理', PARSING: '解析中', CHUNKING: '整理中', EMBEDDING: '建立索引中', NEEDS_REVIEW: '等待审核', FAILED: '处理失败' }
const isReady = (item: DocumentItem) => item.status === 'READY' || item.status === 'INDEXED'
const formatSize = (bytes: number) => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${(bytes / 1024).toFixed(1)} KB`
const formatDate = (value: string) => value && !Number.isNaN(Date.parse(value)) ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) : '—'

export default function KnowledgePage({ accessToken }: { accessToken: string }) {
  const [items, setItems] = useState<DocumentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [operation, setOperation] = useState<{ kind: 'upload' | 'reindex' | 'delete'; name: string; id?: number } | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const operationLock = useRef(false)
  const busy = operation !== null

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    try { setItems(await getDocuments(accessToken, signal)) }
    catch (e) { if (!isAbortError(e)) setError(e instanceof Error ? e.message : '文档列表加载失败，请重试。') }
    finally { if (!signal?.aborted) setLoading(false) }
  }, [accessToken])
  useEffect(() => { const controller = new AbortController(); void refresh(controller.signal); return () => controller.abort() }, [refresh])

  async function upload(file?: File) {
    if (!file || operationLock.current) return
    setError(''); setNotice('')
    if (!/\.(pdf|docx?|md|markdown|txt)$/i.test(file.name)) { setError('请选择 PDF、Word、Markdown 或 TXT 文档。'); return }
    if (file.size === 0) { setError('这份文件是空的，请选择有内容的文档。'); return }
    operationLock.current = true; setOperation({ kind: 'upload', name: file.name })
    try { const item = await uploadDocument(accessToken, file); setNotice(isReady(item) ? `「${file.name}」已准备好，可以回到面试助手提问。` : `「${file.name}」已上传，请查看处理状态。`) }
    catch (e) { setError(e instanceof Error ? e.message : '上传失败，请稍后重试。') }
    finally { await refresh(); operationLock.current = false; setOperation(null) }
  }
  async function runAction(item: DocumentItem, kind: 'reindex' | 'delete') {
    if (operationLock.current) return
    if (kind === 'delete' && !window.confirm(`删除「${item.originalFilename}」？原文件及检索片段会被移除，此操作无法撤销。`)) return
    operationLock.current = true; setOperation({ kind, id: item.id, name: item.originalFilename }); setError(''); setNotice('')
    try { if (kind === 'delete') await deleteDocument(accessToken, item.id); else await reindexDocument(accessToken, item.id); setNotice(kind === 'delete' ? '文档已删除。' : '索引已更新，可以继续提问。') }
    catch (e) { setError(e instanceof Error ? e.message : '操作失败，请稍后重试。') }
    finally { await refresh(); operationLock.current = false; setOperation(null) }
  }

  const readyCount = items.filter(isReady).length
  const failedCount = items.filter(item => item.status === 'FAILED').length
  const filtered = items.filter(item => (filter === 'all' || (filter === 'ready' ? isReady(item) : item.status === 'FAILED')) && (item.originalFilename || '').toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))

  return <section className="kb-page" aria-labelledby="kb-title">
    <header className="kb-heading"><div><div className="kb-eyebrow"><span /> YOUR PERSONAL LIBRARY</div><h2 id="kb-title">让每一次回答，<br className="kb-mobile-break" />都有据可依<span>。</span></h2><p>把简历、项目笔记和面试资料，变成你的专属知识库。</p></div><Link className="kb-button kb-button-light" to="/workbench">去面试助手提问 <Icon name="arrow" /></Link></header>
    <div className="kb-summary" aria-label="知识库概览"><div><span className="kb-summary-icon"><Icon name="book" /></span><div><strong>{loading && !items.length ? '—' : items.length.toString().padStart(2, '0')}</strong><span>全部文档</span></div></div><div><span className="kb-summary-icon"><Icon name="check" /></span><div><strong>{loading && !items.length ? '—' : readyCount.toString().padStart(2, '0')}</strong><span>可用于问答</span></div></div><div><span className="kb-summary-icon"><Icon name="file" /></span><div><strong className="kb-size-value">{loading && !items.length ? '—' : formatSize(items.reduce((total, item) => total + item.fileSize, 0))}</strong><span>文档总大小</span></div></div></div>
    <div className="kb-intake"><div className={`kb-dropzone${dragging ? ' is-dragging' : ''}${busy ? ' is-busy' : ''}`} onDragOver={e => { e.preventDefault(); if (!busy) setDragging(true) }} onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false) }} onDrop={e => { e.preventDefault(); setDragging(false); if (busy) return; if (e.dataTransfer.files.length !== 1) { setError('请一次上传一份文档。'); return }; void upload(e.dataTransfer.files[0]) }}><span className="kb-upload-icon"><Icon name="upload" /></span><h3>{operation?.kind === 'upload' ? '正在整理你的资料…' : dragging ? '松开即可上传' : '给知识库添加一份新资料'}</h3><p>拖拽文件到这里，或从本地选择文档</p><input ref={inputRef} type="file" hidden accept=".pdf,.doc,.docx,.md,.markdown,.txt" disabled={busy} onChange={e => { const file = e.target.files?.[0]; e.target.value = ''; void upload(file) }} /><button className="kb-button kb-button-primary" type="button" disabled={busy} onClick={() => inputRef.current?.click()}><Icon name="upload" />{operation?.kind === 'upload' ? '上传并处理中…' : '选择文档'}</button><div className="kb-formats"><span>PDF</span><span>Word</span><span>Markdown</span><span>TXT</span></div><small>支持可提取文本的文档 · 扫描图片暂不支持</small></div><aside className="kb-guide"><div className="kb-guide-top"><span>从资料到答案</span><Icon name="book" /></div><h3>你的积累，<br />成为回答的依据。</h3><ol><li><span>01</span><div><strong>上传你的资料</strong><p>简历、项目文档、面试笔记</p></div></li><li><span>02</span><div><strong>等待资料就绪</strong><p>系统自动提取并整理文档内容</p></div></li><li><span>03</span><div><strong>带着问题来提问</strong><p>助手检索相关资料，并标注来源</p></div></li></ol></aside></div>
    {operation && <div className="kb-feedback kb-progress" role="status"><span className="kb-spinner" /><span>{operation.kind === 'upload' ? '正在上传并处理' : operation.kind === 'reindex' ? '正在重建索引' : '正在删除'}「{operation.name}」{operation.kind !== 'delete' && '，请稍候，暂时不要离开页面。'}</span></div>}
    {error && <div className="kb-feedback kb-error" role="alert"><span>{error}</span><button type="button" onClick={() => setError('')} aria-label="关闭错误提示">×</button></div>}
    {notice && <div className="kb-feedback kb-success" role="status"><Icon name="check" /><span>{notice}</span></div>}
    <section className="kb-library" aria-labelledby="kb-library-title" aria-busy={loading}><div className="kb-library-heading"><div><h3 id="kb-library-title">我的文档 <span>{items.length}</span></h3><p>管理资料，随时更新你的面试准备。</p></div><button className="kb-button kb-button-light" type="button" disabled={loading || busy} onClick={() => { setError(''); void refresh() }}><Icon name="refresh" />刷新</button></div><div className="kb-toolbar"><div className="kb-filters" aria-label="文档状态筛选">{[{ id: 'all', name: '全部', count: items.length }, { id: 'ready', name: '已就绪', count: readyCount }, { id: 'failed', name: '需处理', count: failedCount }].map(tab => <button key={tab.id} type="button" aria-pressed={filter === tab.id} onClick={() => setFilter(tab.id)}>{tab.name}<span>{tab.count}</span></button>)}</div><label className="kb-search"><Icon name="search" /><input aria-label="搜索文档名称" placeholder="搜索文档名称…" value={query} onChange={e => setQuery(e.target.value)} /></label></div><div className="kb-list-head" aria-hidden="true"><span>文档名称</span><span>状态</span><span>更新时间</span><span>操作</span></div>{loading && !items.length ? <div className="kb-empty" role="status"><span className="kb-spinner" /><h4>正在加载文档…</h4></div> : filtered.length === 0 ? <div className="kb-empty"><Icon name={items.length ? 'search' : 'book'} /><h4>{items.length ? '没有符合条件的文档' : error ? '暂时无法加载文档' : '从第一份资料开始'}</h4><p>{items.length ? '试试其他文件名，或切换状态筛选。' : error ? '请查看上方错误提示，然后点击刷新重试。' : '上传一份简历或项目笔记，让助手更了解你的经历。'}</p>{items.length > 0 && <button className="kb-button kb-button-light" onClick={() => { setQuery(''); setFilter('all') }}>清除筛选</button>}</div> : <ul className="kb-documents">{filtered.map(item => { const active = operation?.id === item.id; return <li className="kb-document" key={item.id}><div className="kb-file-info"><span className={`kb-file-icon kb-file-${item.fileExtension?.toLowerCase()}`}><Icon name="file" /><small>{item.fileExtension === 'markdown' ? 'MD' : (item.fileExtension || 'DOC').toUpperCase()}</small></span><div><strong title={item.originalFilename}>{item.originalFilename || '未命名文档'}</strong><small>{formatSize(item.fileSize)}</small>{item.failureReason && <p className="kb-failure">{item.failureReason}</p>}</div></div><div><span className={`kb-status ${item.status === 'FAILED' ? 'is-failed' : isReady(item) ? 'is-ready' : 'is-pending'}`}><i />{active ? operation.kind === 'delete' ? '删除中' : '重建中' : statusLabels[item.status] || '待处理'}</span></div><time className="kb-date" dateTime={item.updatedAt}>{formatDate(item.updatedAt)}</time><div className="kb-actions"><button type="button" disabled={busy} onClick={() => void runAction(item, 'reindex')} aria-label={`重建索引：${item.originalFilename}`} title="重新解析文档并更新检索索引"><Icon name="refresh" /><span>重建索引</span></button><button className="kb-delete" type="button" disabled={busy} onClick={() => void runAction(item, 'delete')} aria-label={`删除：${item.originalFilename}`} title="删除文档"><Icon name="trash" /></button></div></li> })}</ul>}<footer className="kb-library-footer"><span>显示 {filtered.length} / {items.length} 份文档</span><span><Icon name="check" />仅当前账号可访问</span></footer></section>
  </section>
}
