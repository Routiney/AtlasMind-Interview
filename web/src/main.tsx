import { StrictMode, createContext, useContext, useEffect, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { clearAuthSession, loadAuthSession, saveAuthSession, type AuthSession } from './authStorage'
import { createConversation as createConversationApi, deleteConversation, devLogin, getConversationMessages, getConversations, getCurrentUser, login as loginWithPassword, register as registerUser, updateConversationTitle, type Conversation as ApiConversation, type ConversationMessage, type CurrentUser } from './authApi'
import './style.css'
import { ResumePage } from './ResumePage'
import { requestSse } from './apiClient'
import { validateDisplayName, validatePassword, validateUsername } from './validation'

type Phase = 'idle' | 'thinking' | 'streaming' | 'done' | 'cancelled' | 'error'
type AuthStatus = 'checking' | 'authenticated' | 'anonymous' | 'unavailable'
type StreamEvent = { content?: string; conversation_id?: number }
type ChatMessage = Pick<ConversationMessage, 'id' | 'role' | 'content'>
type ConversationSnapshot = { messages: ChatMessage[]; conversationId: number | null }
type Conversation = ApiConversation & ConversationSnapshot
const DEV_AUTO_LOGIN = import.meta.env.DEV && import.meta.env.VITE_DEV_AUTO_LOGIN === 'true'

type AuthContextValue = {
  authSession: AuthSession | null
  authStatus: AuthStatus
  currentUser: CurrentUser | null
  authBusy: boolean
  authError: string
  authNotice: string
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string, displayName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return value
}

function AuthProvider({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate()
  const [authSession, setAuthSession] = useState<AuthSession | null>(() => loadAuthSession())
  const [authStatus, setAuthStatus] = useState<AuthStatus>(() => authSession || DEV_AUTO_LOGIN ? 'checking' : 'anonymous')
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [authBusy, setAuthBusy] = useState(false)
  const [authError, setAuthError] = useState('')
  const [authNotice, setAuthNotice] = useState('')
  const initialSessionRef = useRef(authSession)
  const initialCheckCompletedRef = useRef(false)

  useEffect(() => {
    function handleUnauthorized() {
      clearAuthSession()
      setAuthSession(null)
      setCurrentUser(null)
      setAuthStatus('anonymous')
      setAuthError('登录状态已过期，请重新登录。')
      navigate('/login', { replace: true })
    }

    window.addEventListener('atlasmind:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('atlasmind:unauthorized', handleUnauthorized)
  }, [navigate])

  useEffect(() => {
    const session = initialSessionRef.current
    if (initialCheckCompletedRef.current) return
    initialCheckCompletedRef.current = true

    async function verifyCurrentUser() {
      try {
        const activeSession = session || (DEV_AUTO_LOGIN ? saveAuthSession(await devLogin()) : null)
        if (!activeSession) {
          setAuthStatus('anonymous')
          return
        }
        if (!session) setAuthSession(activeSession)
        const user = await getCurrentUser(activeSession.accessToken)
        setCurrentUser(user)
        setAuthStatus('authenticated')
      } catch (error) {
        if (error instanceof Error && 'status' in error && error.status === 401) {
          clearAuthSession()
          setAuthSession(null)
          setAuthStatus('anonymous')
          return
        }
        setAuthStatus('unavailable')
        setAuthError(error instanceof Error ? error.message : '无法验证登录状态')
      }
    }

    void verifyCurrentUser()
  }, [])

  async function login(username: string, password: string) {
    setAuthBusy(true)
    setAuthError('')
    setAuthNotice('')
    try {
      const result = await loginWithPassword(username, password)
      setAuthSession(saveAuthSession(result))
      setCurrentUser({ userId: '', username })
      setAuthStatus('authenticated')
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : '登录失败')
      throw error
    } finally {
      setAuthBusy(false)
    }
  }

  async function register(username: string, password: string, displayName: string) {
    setAuthBusy(true)
    setAuthError('')
    setAuthNotice('')
    try {
      await registerUser({ username, password, displayName })
      setAuthNotice('注册成功，请使用新账号登录')
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : '注册失败')
      throw error
    } finally {
      setAuthBusy(false)
    }
  }

  function logout() {
    clearAuthSession()
    setAuthSession(null)
    setCurrentUser(null)
    setAuthStatus('anonymous')
  }

  return <AuthContext.Provider value={{ authSession, authStatus, currentUser, authBusy, authError, authNotice, login, register, logout }}>{children}</AuthContext.Provider>
}

function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { authStatus, authBusy, authError, authNotice, login, register } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [clientError, setClientError] = useState('')
  const isRegister = mode === 'register'

  if (authStatus === 'authenticated') return <Navigate to="/workbench" replace />

  async function submit() {
    if (authBusy) return
    const validationError = isRegister
      ? validateUsername(username) || validatePassword(password) || validateDisplayName(displayName)
      : validateUsername(username) || validatePassword(password)
    if (validationError) {
      setClientError(validationError)
      return
    }
    setClientError('')
    if (isRegister) {
      await register(username, password, displayName)
      navigate('/login')
      return
    }
    await login(username, password)
    navigate('/workbench')
  }

  return <main className="auth-page">
    <div className="auth-intro"><p className="eyebrow">ATLASMIND / PERSONAL INTERVIEW</p><h1>面试准备工作台</h1><p className="sub">把简历、目标岗位和练习过程放在一个清晰的工作区里。</p></div>
    <section className="panel auth-panel">
      <div className="auth-tabs" role="tablist" aria-label="认证模式"><Link className={mode === 'login' ? 'tab active' : 'tab'} to="/login" role="tab" aria-selected={mode === 'login'}>登录</Link><Link className={mode === 'register' ? 'tab active' : 'tab'} to="/register" role="tab" aria-selected={mode === 'register'}>注册</Link></div>
      <div className="auth-form">
        {isRegister && <label className="field"><span>显示名称</span><input maxLength={100} value={displayName} onChange={e => { setDisplayName(e.target.value); setClientError('') }} placeholder="例如：张三" autoComplete="name" /></label>}
        <label className="field"><span>用户名</span><input minLength={3} maxLength={100} value={username} onChange={e => { setUsername(e.target.value); setClientError('') }} placeholder="3 到 100 个字符" autoComplete="username" /></label>
        <label className="field"><span>密码</span><input type="password" minLength={8} maxLength={72} value={password} onChange={e => { setPassword(e.target.value); setClientError('') }} onKeyDown={e => e.key === 'Enter' && void submit()} placeholder="至少 8 个字符" autoComplete={isRegister ? 'new-password' : 'current-password'} /></label>
        <button className="primary-action" onClick={() => void submit()} disabled={authBusy || !username.trim() || !password || (isRegister && !displayName.trim())}>{authBusy ? '处理中…' : isRegister ? '创建账号' : '进入工作台'}</button>
      </div>
      {authNotice && <div className="notice">{authNotice}</div>}{(clientError || authError) && <div className="error" role="alert">{clientError || authError}</div>}
    </section>
  </main>
}

function ProtectedRoute() {
  const { authStatus } = useAuth()
  const location = useLocation()
  if (authStatus === 'checking') return <main className="shell"><div className="status">正在验证登录状态…</div></main>
  if (authStatus !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

function ChatDemo({ initialState, loading = false, hasEarlierMessages = false, onLoadEarlier, onTitleChange, onStateChange }: { initialState?: ConversationSnapshot; loading?: boolean; hasEarlierMessages?: boolean; onLoadEarlier?: () => void; onTitleChange?: (title: string) => void; onStateChange?: (state: ConversationSnapshot) => void }) {
  const { authSession, authStatus } = useAuth()
  const [query, setQuery] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>(initialState?.messages || [])
  const [phase, setPhase] = useState<Phase>(initialState?.messages.length ? 'done' : 'idle')
  const [conversationId, setConversationId] = useState<number | null>(initialState?.conversationId || null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const sendingRef = useRef(false)

  useEffect(() => () => abortControllerRef.current?.abort(), [])
  useEffect(() => {
    onStateChange?.({ messages, conversationId })
  }, [messages, conversationId])
  useEffect(() => {
    if (!initialState) return
    setMessages(initialState.messages)
    setConversationId(initialState.conversationId)
    setPhase(initialState.messages.length ? 'done' : 'idle')
  }, [initialState?.messages, initialState?.conversationId])

  async function send() {
    const trimmedQuery = query.trim()
    if (sendingRef.current || !trimmedQuery || !authSession || authStatus !== 'authenticated' || phase === 'streaming' || phase === 'thinking') return
    sendingRef.current = true
    const controller = new AbortController()
    abortControllerRef.current = controller
    const assistantMessageId = `pending-${Date.now()}`
    setMessages(current => [...current, { id: `user-${assistantMessageId}`, role: 'USER', content: trimmedQuery }, { id: assistantMessageId, role: 'ASSISTANT', content: '' }])
    onTitleChange?.(trimmedQuery.slice(0, 28))
    setPhase('streaming')
    try {
      const response = await requestSse('/api/agents/PlexusAgent/execute', { token: authSession.accessToken, signal: controller.signal, body: { query: trimmedQuery, session_id: 'web-demo', conversation_id: conversationId ?? undefined, stream: true } })
      if (!response.body) throw new Error('服务没有返回 SSE 流')
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let eventType = 'message'
      let data = ''
      while (true) {
        const result = await reader.read()
        if (result.done) break
        buffer += decoder.decode(result.value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('event:')) eventType = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trim()
          else if (line === '') {
            if (eventType === 'thinking') setPhase('thinking')
            if (eventType === 'chunk') { const parsed = JSON.parse(data) as StreamEvent; if (parsed.conversation_id) setConversationId(parsed.conversation_id); setMessages(current => current.map(message => message.id === assistantMessageId ? { ...message, content: message.content + (parsed.content || '') } : message)); setPhase('streaming') }
            if (eventType === 'final') { const parsed = JSON.parse(data) as StreamEvent; if (parsed.conversation_id) setConversationId(parsed.conversation_id); setMessages(current => current.map(message => message.id === assistantMessageId ? { ...message, content: parsed.content || '' } : message)) }
            if (eventType === 'done') setPhase('done')
            eventType = 'message'
            data = ''
          }
        }
      }
      setPhase(current => current === 'error' || current === 'cancelled' ? current : 'done')
    } catch (error) {
      if (controller.signal.aborted) { setPhase('cancelled'); return }
      setPhase('error')
      setMessages(current => current.map(message => message.id === assistantMessageId ? { ...message, content: error instanceof Error ? error.message : '请求失败' } : message))
    } finally {
      sendingRef.current = false
      if (abortControllerRef.current === controller) abortControllerRef.current = null
    }
  }

  return <section className="chat-stage">
    {loading ? <div className="chat-state"><span className="state-spinner" />正在加载会话…</div> : messages.length ? <div className="chat-transcript">{hasEarlierMessages && <button className="load-earlier" type="button" onClick={onLoadEarlier}>加载更早消息</button>}{messages.map((message, index) => message.role === 'USER' ? <div key={message.id} className="message-row user-message"><div className="user-bubble">{message.content}</div></div> : message.role === 'ASSISTANT' ? <div key={message.id} className="message-row assistant-message"><div className="assistant-mark" aria-hidden="true">✦</div><div className="assistant-body"><div className="stream-status"><span className={`dot ${index === messages.length - 1 ? phase : 'done'}`} />{index === messages.length - 1 ? phase === 'thinking' ? '准备回答' : phase === 'streaming' ? '正在生成' : phase === 'done' ? '已完成' : phase === 'cancelled' ? '已停止' : '回答' : '已完成'}</div><div className="answer">{message.content || '正在整理你的问题…'}</div>{index === messages.length - 1 && (phase === 'done' || phase === 'cancelled' || phase === 'error') && <div className="message-actions"><button title="复制回答" onClick={() => void navigator.clipboard?.writeText(message.content)}>▣</button><button title="重新生成" onClick={() => void send()}>↻</button><button title="回答有帮助">♡</button></div>}</div></div> : null)}</div> : <div className="chat-welcome"><span className="brand-mark" aria-hidden="true">✦</span><h2>开始一场面试练习</h2><p>告诉我目标岗位，或者直接说说你想练习的题目。</p></div>}
    <div className="chat-composer"><div className="composer-input"><input value={query} disabled={loading} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && void send()} placeholder={loading ? '正在加载会话…' : '向面试助手提问'} /><div className="composer-tools"><button className="tool-button" type="button">＋ 添加简历上下文</button><button className="tool-button" type="button">快速模式⌄</button><span className="composer-hint">Enter 发送</span><button className="send-button" onClick={() => void send()} disabled={loading || !query.trim() || phase === 'streaming' || phase === 'thinking'} aria-label="发送">↑</button><button className="stop-button" onClick={() => abortControllerRef.current?.abort()} disabled={phase !== 'streaming' && phase !== 'thinking'} aria-label="停止">■</button></div></div></div>
  </section>
}

function WorkbenchPage({ children }: { children?: React.ReactNode }) {
  const { authSession, authStatus, currentUser, logout } = useAuth()
  const navigate = useNavigate()
  const [sidebarOpen, setSidebarOpen] = useState(() => window.innerWidth > 720)
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null)
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [conversationError, setConversationError] = useState('')
  const [conversationLoading, setConversationLoading] = useState(true)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [messagePage, setMessagePage] = useState(0)
  const [hasEarlierMessages, setHasEarlierMessages] = useState(false)
  const [creatingConversation, setCreatingConversation] = useState(false)
  const [busyConversationIds, setBusyConversationIds] = useState<Set<number>>(new Set())
  const menu = [
    { to: '/workbench', label: '面试助手', icon: '⌂' },
    { to: '/resume', label: '简历档案', icon: '▤' },
    { to: '/jobs', label: '岗位匹配', icon: '⌁' },
    { to: '/knowledge', label: '知识库', icon: '◇' },
    { to: '/interview', label: '模拟面试', icon: '◌' },
    { to: '/review', label: '复盘记录', icon: '☷' },
  ]
  async function refreshConversations(signal?: AbortSignal) {
    if (!authSession || authStatus !== 'authenticated') return
    setConversationLoading(true)
    const firstPage = await getConversations(authSession.accessToken, 0, 20, signal)
    let items = firstPage.items
    if (items.length === 0) items = [await createConversationApi(authSession.accessToken)]
    setConversations(current => items.map(item => current.find(existing => existing.id === item.id) || { ...item, messages: [], conversationId: item.id }))
    setActiveConversationId(current => current ?? items[0]?.id ?? null)
    setConversationLoading(false)
  }

  useEffect(() => {
    if (!authSession || authStatus !== 'authenticated') return
    const controller = new AbortController()
    void refreshConversations(controller.signal).catch(error => {
      if (!controller.signal.aborted) {
        setConversationLoading(false)
        setConversationError(error instanceof Error ? error.message : '无法加载会话')
      }
    })
    return () => controller.abort()
  }, [authSession, authStatus])

  useEffect(() => {
    if (!authSession || authStatus !== 'authenticated' || !activeConversationId) return
    const selected = conversations.find(item => item.id === activeConversationId)
    if (!selected || selected.messages.length) return
    setMessagesLoading(true)
    setMessagePage(0)
    const controller = new AbortController()
    void getConversationMessages(authSession.accessToken, activeConversationId, 0, 30, controller.signal).then(result => {
      const messages = result.items.filter(message => message.role !== 'SYSTEM').reverse().map(message => ({ id: message.id, role: message.role as 'USER' | 'ASSISTANT', content: message.content }))
      setConversations(items => items.map(item => item.id === activeConversationId ? { ...item, messages, conversationId: activeConversationId } : item))
      setHasEarlierMessages(result.hasNext)
    }).catch(error => {
      if (!controller.signal.aborted) setConversationError(error instanceof Error ? error.message : '无法加载会话消息')
    }).finally(() => {
      if (!controller.signal.aborted) setMessagesLoading(false)
    })
    return () => controller.abort()
  }, [activeConversationId, authSession, authStatus])

  async function createConversation() {
    if (!authSession || creatingConversation) return
    try {
      setCreatingConversation(true)
      setConversationError('')
      const item = await createConversationApi(authSession.accessToken)
      const conversation = { ...item, messages: [], conversationId: item.id }
      setConversations(items => [...items, conversation])
      setActiveConversationId(item.id)
    } catch (error) {
      setConversationError(error instanceof Error ? error.message : '无法创建会话')
    } finally {
      setCreatingConversation(false)
    }
  }

  function selectConversation(id: number) {
    setConversationError('')
    if (id === activeConversationId) return
    setConversations(items => items.map(item => item.id === id ? { ...item, messages: [] } : item))
    setActiveConversationId(id)
  }
  async function loadEarlierMessages() {
    if (!authSession || !activeConversationId || messagesLoading || !hasEarlierMessages) return
    const nextPage = messagePage + 1
    setMessagesLoading(true)
    try {
      const result = await getConversationMessages(authSession.accessToken, activeConversationId, nextPage, 30)
      const olderMessages = result.items.filter(message => message.role !== 'SYSTEM').reverse().map(message => ({ id: message.id, role: message.role as 'USER' | 'ASSISTANT', content: message.content }))
      setConversations(items => items.map(item => item.id === activeConversationId ? { ...item, messages: [...olderMessages, ...item.messages] } : item))
      setMessagePage(nextPage)
      setHasEarlierMessages(result.hasNext)
    } catch (error) {
      setConversationError(error instanceof Error ? error.message : '无法加载更早消息')
    } finally {
      setMessagesLoading(false)
    }
  }
  async function renameConversationById(id: number, currentTitle: string) {
    const nextTitle = window.prompt('重命名会话', currentTitle)?.trim()
    if (!nextTitle || nextTitle === currentTitle || !authSession) return
    const previous = conversations
    setConversations(items => items.map(item => item.id === id ? { ...item, title: nextTitle } : item))
    try {
      setBusyConversationIds(ids => new Set(ids).add(id))
      await updateConversationTitle(authSession.accessToken, id, nextTitle)
    } catch (error) {
      setConversations(previous)
      setConversationError(error instanceof Error ? error.message : '无法重命名会话')
    } finally {
      setBusyConversationIds(ids => { const next = new Set(ids); next.delete(id); return next })
    }
  }
  async function removeConversation(id: number) {
    if (!authSession || busyConversationIds.has(id) || !window.confirm('删除后将无法恢复，确定删除这个会话吗？')) return
    const previous = conversations
    const previousActiveId = activeConversationId
    const remaining = conversations.filter(item => item.id !== id)
    setConversations(remaining)
    if (activeConversationId === id) setActiveConversationId(remaining[0]?.id ?? null)
    try {
      setBusyConversationIds(ids => new Set(ids).add(id))
      await deleteConversation(authSession.accessToken, id)
    } catch (error) {
      setConversations(previous)
      setActiveConversationId(previousActiveId)
      setConversationError(error instanceof Error ? error.message : '无法删除会话')
    } finally {
      setBusyConversationIds(ids => { const next = new Set(ids); next.delete(id); return next })
    }
  }
  function renameConversation(title: string) {
    setConversations(items => items.map(item => item.id === activeConversationId ? { ...item, title } : item))
    if (authSession && activeConversationId && title !== '新建对话') void updateConversationTitle(authSession.accessToken, activeConversationId, title)
  }
  function saveConversationState(state: ConversationSnapshot) {
    setConversations(items => items.map(item => item.id === activeConversationId ? { ...item, ...state } : item))
  }

  return <main className={sidebarOpen ? 'app-shell' : 'app-shell sidebar-collapsed'}>
    <aside className="sidebar">
      <div className="sidebar-top"><Link className="brand" to="/workbench">ATLASMIND</Link><button className="icon-button" onClick={() => setSidebarOpen(open => !open)} aria-label={sidebarOpen ? '收起侧边栏' : '展开侧边栏'}>◀</button></div>
      <nav className="sidebar-nav" aria-label="工作台导航"><p className="nav-section-title">工作区</p>{menu.map(item => <NavLink key={item.to} to={item.to} className={({ isActive }) => isActive ? 'side-item active' : 'side-item'}><span className="side-icon" aria-hidden="true">{item.icon}</span><span>{item.label}</span>{item.to !== '/workbench' && <small>规划中</small>}</NavLink>)}</nav>
      <div className="sidebar-bottom"><div className="user-card"><span className="avatar">{currentUser?.username?.slice(0, 1).toUpperCase() || 'D'}</span><span className="user-copy"><strong>{currentUser?.username || 'dev-user'}</strong><small>个人空间</small></span><button className="more-button" onClick={() => { logout(); navigate('/login') }} aria-label="退出登录">⋯</button></div></div>
    </aside>
    <section className="main-workspace"><header className="workspace-header"><div><span className="workspace-kicker">PERSONAL INTERVIEW ASSISTANT</span><h1>{children ? '简历档案' : '面试助手'}</h1></div><div className="header-actions"><span className="connection-state"><span className="connection-dot" />已连接</span><button className="header-icon" onClick={() => setSidebarOpen(open => !open)} aria-label="切换侧边栏">☰</button></div></header>{children ? <div className="workspace-content standalone-content">{children}</div> : <div className="workspace-body"><aside className="conversation-sidebar"><div className="conversation-heading"><span>会话</span><button className="conversation-add" disabled={creatingConversation} onClick={() => void createConversation()} aria-label="新建会话">{creatingConversation ? '…' : '＋'}</button></div>{conversationError && <div className="conversation-error"><span>{conversationError}</span><button type="button" onClick={() => void refreshConversations()}>重试</button></div>}{conversationLoading ? <div className="conversation-state"><span className="state-spinner" />加载中…</div> : conversations.length ? <div className="conversation-list">{conversations.map(item => { const busy = busyConversationIds.has(item.id); return <div key={item.id} className={item.id === activeConversationId ? 'conversation-item active' : 'conversation-item'}><button className="conversation-select" disabled={busy} onClick={() => selectConversation(item.id)}><span className="conversation-icon">◌</span><span>{item.title}</span></button><span className="conversation-actions"><button type="button" disabled={busy} title="重命名" onClick={() => void renameConversationById(item.id, item.title)}>✎</button><button type="button" disabled={busy} title="删除" onClick={() => void removeConversation(item.id)}>×</button></span></div> })}</div> : <div className="conversation-state">还没有会话</div>}</aside><div className="workspace-content"><div className="workspace-context"><span>当前对话</span><span className="context-line" /> <span className="context-muted">{conversations.find(item => item.id === activeConversationId)?.title || '选择一个会话'}</span></div><ChatDemo key={activeConversationId ?? 'empty'} loading={messagesLoading} hasEarlierMessages={hasEarlierMessages} onLoadEarlier={() => void loadEarlierMessages()} initialState={conversations.find(item => item.id === activeConversationId)} onTitleChange={renameConversation} onStateChange={saveConversationState} /></div></div>}</section>
  </main>
}

function ResumeWorkbenchPage() {
  const { currentUser, authSession } = useAuth()
  if (!currentUser || !authSession) return null
  return <WorkbenchPage><ResumePage key={currentUser.username} username={currentUser.username} accessToken={authSession.accessToken} /></WorkbenchPage>
}

function PlaceholderPage({ title, description }: { title: string; description: string }) {
  const { currentUser, authSession } = useAuth()
  if (title === '简历档案' && currentUser && authSession) return <ResumePage key={currentUser.username} username={currentUser.username} accessToken={authSession.accessToken} />
  return <main className="placeholder-page"><Link className="brand" to="/workbench">ATLASMIND</Link><span className="workspace-kicker">PERSONAL INTERVIEW WORKBENCH</span><h1>{title}</h1><p>{description}</p><Link className="back-link" to="/workbench">返回工作台</Link></main>
}

function App() {
  return <BrowserRouter><AuthProvider><Routes><Route path="/login" element={<AuthPage mode="login" />} /><Route path="/register" element={<AuthPage mode="register" />} /><Route element={<ProtectedRoute />}><Route path="/workbench" element={<WorkbenchPage />} /><Route path="/resume" element={<ResumeWorkbenchPage />} /><Route path="/jobs" element={<PlaceholderPage title="岗位匹配" description="导入目标岗位后，生成能力要求与准备重点。" />} /><Route path="/knowledge" element={<PlaceholderPage title="知识库" description="沉淀面试知识和岗位资料，后续接入检索能力。" />} /><Route path="/interview" element={<PlaceholderPage title="模拟面试" description="从结构化问题开始，逐步接入 InterviewManagerAgent。" />} /><Route path="/review" element={<PlaceholderPage title="复盘记录" description="查看练习记录、回答证据和改进建议。" />} /></Route><Route path="/" element={<Navigate to="/workbench" replace />} /><Route path="*" element={<Navigate to="/" replace />} /></Routes></AuthProvider></BrowserRouter>
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
