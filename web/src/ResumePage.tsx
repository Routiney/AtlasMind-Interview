import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getResume, saveResume, type ResumeProfile } from './authApi'
import { validateResumeDraft } from './validation'

type Draft = Omit<ResumeProfile, 'id' | 'userId' | 'createdAt' | 'updatedAt'>
const emptyDraft: Draft = { name: '', targetRole: '', summary: '', education: '', internship: '', projects: '', skills: '' }

export function ResumePage({ username, accessToken }: { username: string; accessToken: string }) {
  const [draft, setDraft] = useState<Draft>(emptyDraft)
  const [saved, setSaved] = useState<Draft>(emptyDraft)
  const [loadStatus, setLoadStatus] = useState<'loading' | 'success' | 'empty' | 'error'>('loading')
  const [retry, setRetry] = useState(0)
  const savingRef = useRef(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoadStatus('loading')
    setError('')
    async function load() {
      try {
        const profile = await getResume(accessToken, controller.signal)
        if (!active) return
        const next = profile ? { name: profile.name, targetRole: profile.targetRole, summary: profile.summary, education: profile.education, internship: profile.internship, projects: profile.projects, skills: profile.skills } : emptyDraft
        setDraft(next)
        setSaved(next)
        setLoadStatus(profile ? 'success' : 'empty')
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : '无法加载简历档案')
          setLoadStatus('error')
        }
      }
    }
    void load()
    return () => { active = false; controller.abort() }
  }, [accessToken, retry])

  function update(field: keyof Draft, value: string) {
    setDraft(previous => ({ ...previous, [field]: value }))
    setNotice('')
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingRef.current || !dirty || (loadStatus !== 'success' && loadStatus !== 'empty')) return
    const validationError = validateResumeDraft(draft)
    if (validationError) { setError(validationError); return }
    savingRef.current = true
    setSaving(true)
    setNotice('')
    setError('')
    try {
      const profile = await saveResume(accessToken, { ...draft, name: draft.name.trim(), targetRole: draft.targetRole.trim() })
      const next = { name: profile.name, targetRole: profile.targetRole, summary: profile.summary, education: profile.education, internship: profile.internship, projects: profile.projects, skills: profile.skills }
      setDraft(next)
      setSaved(next)
      setLoadStatus('success')
      setNotice('简历档案已保存到服务器。')
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '保存失败')
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  return <main className="resume-page">
    <Link className="back-link" to="/workbench">返回工作台</Link>
    <header className="resume-heading"><h1>简历档案</h1><span>服务器档案</span></header>
    <p className="resume-note">当前账号：{username} · 内容会保存到个人简历数据库。</p>
    {loadStatus === 'loading' ? <p className="status" role="status">正在加载简历档案…</p> : loadStatus === 'error' ? <section className="resume-load-error"><p className="error" role="alert">{error}</p><button type="button" onClick={() => { setLoadStatus('loading'); setRetry(value => value + 1) }}>重新加载</button></section> : <form className="resume-form" onSubmit={save}>
      {loadStatus === 'empty' && <p className="status">尚未创建简历档案</p>}
      <fieldset className="resume-edit-fields" disabled={saving}>
      <div className="resume-fields"><label className="field"><span>姓名</span><input required maxLength={100} autoComplete="name" value={draft.name} onChange={event => update('name', event.target.value)} /></label><label className="field"><span>目标岗位</span><input required maxLength={100} value={draft.targetRole} onChange={event => update('targetRole', event.target.value)} placeholder="例如：Java 后端工程师" /></label></div>
      <label className="field"><span>个人简介</span><textarea rows={5} maxLength={2000} value={draft.summary} onChange={event => update('summary', event.target.value)} /></label>
      <label className="field"><span>教育经历</span><textarea rows={5} maxLength={4000} value={draft.education} onChange={event => update('education', event.target.value)} placeholder="学校、专业、学历和时间" /></label>
      <label className="field"><span>实习经历</span><textarea rows={5} maxLength={4000} value={draft.internship} onChange={event => update('internship', event.target.value)} placeholder="公司、岗位、时间和主要工作" /></label>
      <label className="field"><span>项目经历</span><textarea rows={6} maxLength={6000} value={draft.projects} onChange={event => update('projects', event.target.value)} placeholder="项目背景、你的职责、技术方案和结果" /></label>
      <label className="field"><span>专业技能</span><textarea rows={4} maxLength={2000} value={draft.skills} onChange={event => update('skills', event.target.value)} placeholder="例如：Java、Spring Boot、PostgreSQL、Docker" /></label>
      <div className="resume-actions"><button type="submit" disabled={saving || !dirty}>{saving ? '保存中…' : '保存简历档案'}</button><button type="button" className="resume-reset" disabled={!dirty} onClick={() => { setDraft(saved); setError(''); setNotice('已恢复到上次保存的内容。') }}>撤销未保存修改</button><span>{dirty ? '有未保存的修改' : '内容已同步'}</span></div>
      </fieldset>
      {error && <p className="error" role="alert">{error}</p>}
      <p className="notice" role="status">{notice}</p>
    </form>}
  </main>
}
