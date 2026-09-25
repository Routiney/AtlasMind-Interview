import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isAbortError } from './apiClient'
import { generatePlanningResult, getPlanningHistory, getPlanningResult, getResume, type PlanningHistoryItem, type PlanningResult, type ResumeProfile } from './authApi'

function Confidence({ value }: { value: 'high' | 'medium' | 'low' }) {
  return <span className={`confidence confidence-${value}`}>{value === 'high' ? '证据充分' : value === 'medium' ? '需要核验' : '证据有限'}</span>
}

function List({ items, empty = '暂无记录' }: { items: string[]; empty?: string }) {
  return items.length ? <ul className="planning-list">{items.map((item, index) => <li key={`${item}-${index}`}>{presentText(item)}</li>)}</ul> : <p className="empty-line">{empty}</p>
}

function presentText(value: string) {
  return value
    .replaceAll('experience_supported', '有经历支撑')
    .replaceAll('self_declared_only', '仅在技能栏提及')
    .replaceAll('evidence_strength', '证据来源')
    .replaceAll('resume_evidence', '简历证据')
    .replaceAll('job_match', '岗位匹配')
    .replaceAll('job_directions', '岗位方向')
    .replaceAll('task_type', '任务类型')
    .replaceAll('query', '检索内容')
}

type PlanningAction = 'generating' | 'loading-plan' | null

export function PlanningPage({ accessToken }: { accessToken: string }) {
  const [resume, setResume] = useState<ResumeProfile | null>(null)
  const [resumeLoading, setResumeLoading] = useState(true)
  const [history, setHistory] = useState<PlanningHistoryItem[]>([])
  const [historyLoading, setHistoryLoading] = useState(true)
  const [weeks, setWeeks] = useState(4)
  const [hoursPerWeek, setHoursPerWeek] = useState(8)
  const [jobDescription, setJobDescription] = useState('')
  const [researchMarket, setResearchMarket] = useState(false)
  const [result, setResult] = useState<PlanningResult | null>(null)
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)
  const [action, setAction] = useState<PlanningAction>(null)
  const [error, setError] = useState('')
  const generationControllerRef = useRef<AbortController | null>(null)
  const planLoadControllerRef = useRef<AbortController | null>(null)
  const planLoadSequenceRef = useRef(0)
  const historySequenceRef = useRef(0)
  const autoLoadedHistoryRef = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    void getResume(accessToken, controller.signal).then(setResume).catch(loadError => {
      if (!controller.signal.aborted) setError(loadError instanceof Error ? loadError.message : '无法加载简历档案')
    }).finally(() => {
      if (!controller.signal.aborted) setResumeLoading(false)
    })
    return () => controller.abort()
  }, [accessToken])

  useEffect(() => {
    autoLoadedHistoryRef.current = false
    const controller = new AbortController()
    const sequence = ++historySequenceRef.current
    setHistoryLoading(true)
    void getPlanningHistory(accessToken, controller.signal).then(items => {
      if (sequence !== historySequenceRef.current) return
      setHistory(items)
      if (!autoLoadedHistoryRef.current && items[0]) {
        autoLoadedHistoryRef.current = true
        void loadPlan(items[0].id)
      }
    }).catch(loadError => {
      if (!controller.signal.aborted && sequence === historySequenceRef.current) setError(loadError instanceof Error ? loadError.message : '无法加载历史规划')
    }).finally(() => {
      if (!controller.signal.aborted && sequence === historySequenceRef.current) setHistoryLoading(false)
    })
    return () => controller.abort()
  }, [accessToken])

  useEffect(() => () => {
    generationControllerRef.current?.abort()
    planLoadControllerRef.current?.abort()
  }, [])

  async function loadPlan(planId: number) {
    planLoadControllerRef.current?.abort()
    const controller = new AbortController()
    planLoadControllerRef.current = controller
    const sequence = ++planLoadSequenceRef.current
    setAction('loading-plan')
    setSelectedPlanId(planId)
    setError('')
    try {
      const next = await getPlanningResult(accessToken, planId, controller.signal)
      if (!controller.signal.aborted && sequence === planLoadSequenceRef.current) setResult(next)
    } catch (loadError) {
      if (!controller.signal.aborted && sequence === planLoadSequenceRef.current) setError(loadError instanceof Error ? loadError.message : '无法读取历史规划')
    } finally {
      if (!controller.signal.aborted && sequence === planLoadSequenceRef.current) setAction(null)
    }
  }

  function requestId() {
    return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (action || !resume) return
    const controller = new AbortController()
    generationControllerRef.current = controller
    setAction('generating')
    setSelectedPlanId(null)
    setError('')
    setResult(null)
    try {
      const next = await generatePlanningResult(accessToken, { requestId: requestId(), jobDescription, weeks, hoursPerWeek, researchMarket }, controller.signal)
      if (controller.signal.aborted) return
      setResult(next)
      setSelectedPlanId(next.plan_id ?? null)
      const historySequence = ++historySequenceRef.current
      setHistoryLoading(true)
      try {
        const refreshedHistory = await getPlanningHistory(accessToken, controller.signal)
        if (!controller.signal.aborted && historySequence === historySequenceRef.current) setHistory(refreshedHistory)
      } catch (historyError) {
        if (!controller.signal.aborted && historySequence === historySequenceRef.current) {
          setError(historyError instanceof Error ? historyError.message : '历史规划刷新失败')
        }
      } finally {
        if (historySequence === historySequenceRef.current) setHistoryLoading(false)
      }
    } catch (planningError) {
      if (isAbortError(planningError)) {
        setError('已停止当前规划请求。若服务端已经完成生成，请刷新历史记录确认。')
      } else {
        setError(planningError instanceof Error ? planningError.message : '职业规划生成失败')
      }
    } finally {
      if (generationControllerRef.current === controller) generationControllerRef.current = null
      setAction(null)
    }
  }

  if (resumeLoading) return <main className="planning-page"><div className="planning-loading"><span className="state-spinner" />正在读取简历档案…</div></main>

  return <main className="planning-page">
    <Link className="back-link" to="/workbench">返回面试助手</Link>
    <header className="planning-heading"><div><span className="workspace-kicker">CAREER PLANNING / RESEARCH WORKFLOW</span><h1>职业规划</h1><p>把简历证据、岗位要求和准备节奏整理成一份可执行的路线。</p></div><span className="planning-mark" aria-hidden="true">↗</span></header>
    {!resume ? <section className="planning-empty"><span className="empty-kicker">PROFILE REQUIRED</span><h2>先完善简历档案</h2><p>职业规划会使用目标岗位、项目、实习和技能字段，当前还没有可分析的档案。</p><Link className="primary-link" to="/resume">打开简历档案</Link></section> : <>
      <section className="planning-history"><div className="section-heading"><div><span className="section-kicker">ARCHIVE / 历史规划</span><h2>之前生成的计划</h2></div><span>{history.length} 份</span></div>{historyLoading ? <div className="history-state">正在读取历史规划…</div> : history.length ? <div className="history-list">{history.map(item => <button type="button" className={selectedPlanId === item.id ? 'history-item active' : 'history-item'} key={item.id} disabled={action !== null} onClick={() => void loadPlan(item.id)}><span><strong>{item.targetRole || '未命名岗位方向'}</strong><small>{new Date(item.createdAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })} · {item.weeks} 周 · 每周 {item.hoursPerWeek} 小时</small></span><span aria-hidden="true">→</span></button>)}</div> : <div className="history-state">生成后的职业规划会保存在这里。</div>}</section>
      <form className="planning-setup" onSubmit={submit}>
        <div className="setup-profile"><span className="setup-label">当前档案</span><strong>{resume.targetRole || '未设置目标岗位'}</strong><span>{resume.name || '未填写姓名'} · {resume.projects ? '已有项目证据' : '项目经历待补充'}</span></div>
        <label className="planning-field planning-job"><span>岗位描述 <em>可选</em></span><textarea value={jobDescription} onChange={event => setJobDescription(event.target.value)} placeholder="粘贴一个具体岗位描述，规划会优先按该岗位分析缺口。" rows={4} maxLength={8000} /></label>
        <div className="planning-controls"><label className="planning-field"><span>准备周期</span><select value={weeks} onChange={event => setWeeks(Number(event.target.value))} disabled={action !== null}>{[2, 4, 6, 8, 12].map(value => <option key={value} value={value}>{value} 周</option>)}</select></label><label className="planning-field"><span>每周投入</span><select value={hoursPerWeek} onChange={event => setHoursPerWeek(Number(event.target.value))} disabled={action !== null}>{[4, 6, 8, 10, 12, 16].map(value => <option key={value} value={value}>{value} 小时</option>)}</select></label><label className="planning-toggle"><input type="checkbox" checked={researchMarket} onChange={event => setResearchMarket(event.target.checked)} disabled={action !== null} /><span><strong>研究公开岗位</strong><small>补充近期技术要求和招聘关键词</small></span></label><button className="planning-submit" type="submit" disabled={action !== null}>{action === 'generating' ? '正在规划…' : result ? '重新生成规划' : '开始职业规划'} <span aria-hidden="true">→</span></button></div>
      </form>
      {error && <div className="planning-error" role="alert">{error}</div>}
      {action === 'generating' && <div className="planning-progress"><span className="state-spinner" /><div><strong>规划 Agent 正在工作</strong><span>拆解任务、整理证据并生成学习路线，这通常需要一点时间。</span></div><button type="button" className="planning-cancel" onClick={() => generationControllerRef.current?.abort()}>停止生成</button></div>}
      {action === 'loading-plan' && <div className="planning-progress"><span className="state-spinner" /><div><strong>正在读取历史计划</strong><span>正在恢复完整报告内容。</span></div></div>}
      {result && <PlanningResultView result={result} />}
    </>}
  </main>
}

function PlanningResultView({ result }: { result: PlanningResult }) {
  const assessment = result.assessment
  const plan = result.learning_plan
  return <div className="planning-result">
    <section className="planning-overview"><div><span className="section-kicker">REPORT WRITER / 总结</span><h2>{presentText(assessment.overall_assessment)}</h2><p>{presentText(plan.rationale)}</p></div><div className="overview-stats"><div><strong>{result.research_plan.tasks.length}</strong><span>研究任务</span></div><div><strong>{plan.duration_weeks}</strong><span>周计划</span></div><div><strong>{plan.hours_per_week}</strong><span>小时 / 周</span></div></div></section>
    <section className="planning-section"><div className="section-heading"><div><span className="section-kicker">PLANNER / 任务拆解</span><h2>这份结论是怎么来的</h2></div><span>{result.task_summaries.length} 项摘要</span></div><div className="task-grid">{result.research_plan.tasks.map((task, index) => { const summary = result.task_summaries.find(item => item.task_id === task.task_id); return <article className="task-item" key={task.task_id}><div className="task-number">0{index + 1}</div><div><h3>{presentText(task.title)}</h3><p>{presentText(task.intent)}</p>{summary && <><List items={summary.findings} /><div className="task-meta"><Confidence value={summary.confidence} /><span>{summary.evidence.length} 条证据</span>{summary.agent_name && <span>{summary.agent_name}</span>}</div></>}</div></article> })}</div></section>
    <section className="planning-columns"><div className="planning-section"><div className="section-heading"><div><span className="section-kicker">ASSESSMENT / 优势</span><h2>可以继续放大的能力</h2></div></div><div className="assessment-list">{assessment.strengths.map((item, index) => <article className="assessment-item" key={`${item.area}-${index}`}><div><strong>{presentText(item.area)}</strong><Confidence value={item.confidence} /></div><p>{presentText(item.conclusion)}</p><small>{presentText(item.evidence.join(' · ') || '未标注具体证据')}</small></article>)}</div>{!assessment.strengths.length && <p className="empty-line">暂未识别出有足够证据的优势</p>}</div><div className="planning-section"><div className="section-heading"><div><span className="section-kicker">ASSESSMENT / 缺口</span><h2>优先补齐的证据</h2></div></div><div className="assessment-list">{assessment.gaps.map((item, index) => <article className="assessment-item gap-item" key={`${item.area}-${index}`}><div><strong>{presentText(item.area)}</strong><Confidence value={item.confidence} /></div><p>{presentText(item.conclusion)}</p><small>{presentText(item.evidence.join(' · ') || '建议通过项目或练习验证')}</small></article>)}</div>{!assessment.gaps.length && <p className="empty-line">暂未识别出明确缺口</p>}</div></section>
    <section className="planning-section"><div className="section-heading"><div><span className="section-kicker">ROLE FIT / 方向</span><h2>适合优先验证的岗位方向</h2></div></div><div className="role-list">{assessment.role_recommendations.map((role, index) => <article className="role-item" key={`${role.role}-${index}`}><div className="role-title"><span>0{index + 1}</span><strong>{presentText(role.role)}</strong></div><p>{presentText(role.reason)}</p><div className="role-tags">{role.strengths.map(item => <span className="tag positive" key={item}>{presentText(item)}</span>)}{role.gaps.map(item => <span className="tag gap" key={item}>补 {presentText(item)}</span>)}</div></article>)}</div>{!assessment.role_recommendations.length && <p className="empty-line">暂未形成明确岗位方向</p>}</section>
    <section className="planning-section"><div className="section-heading"><div><span className="section-kicker">LEARNING PLAN / 执行</span><h2>按阶段推进</h2></div><span>{presentText(plan.target_role)}</span></div><div className="phase-list">{plan.phases.map((phase, index) => <article className="phase-item" key={`${phase.phase}-${index}`}><div className="phase-index">{String(index + 1).padStart(2, '0')}</div><div className="phase-content"><div className="phase-title"><h3>{presentText(phase.phase)}</h3><span>{phase.estimated_hours} 小时</span></div><p>{presentText(phase.objective)}</p><List items={phase.tasks} /><div className="deliverable"><span>产出</span>{presentText(phase.deliverables.join(' · ') || '待补充验收产出')}</div></div></article>)}</div></section>
    <section className="planning-footer"><div><span className="section-kicker">NEXT ACTIONS</span><h2>下一步</h2><List items={assessment.next_actions} empty="根据第一阶段产出再调整计划" /></div><div><span className="section-kicker">LIMITS</span><h2>需要知道的边界</h2><List items={[...result.evidence_limits, ...assessment.evidence_limits]} empty="当前没有额外限制" /></div></section>
  </div>
}
