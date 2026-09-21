export type ResumeDraftValues = {
  name: string
  targetRole: string
  summary: string
  education: string
  internship: string
  projects: string
  skills: string
}

export function validateUsername(value: string): string {
  const length = value.trim().length
  return length < 3 || length > 100 ? '用户名长度必须为 3 到 100 个字符。' : ''
}

export function validatePassword(value: string): string {
  return value.length < 8 || value.length > 72 ? '密码长度必须为 8 到 72 个字符。' : ''
}

export function validateDisplayName(value: string): string {
  const length = value.trim().length
  return !length ? '显示名称不能为空。' : length > 100 ? '显示名称不能超过 100 个字符。' : ''
}

export function validateResumeDraft(draft: ResumeDraftValues): string {
  if (!draft.name.trim()) return '姓名不能为空。'
  if (draft.name.trim().length > 100) return '姓名不能超过 100 个字符。'
  if (!draft.targetRole.trim()) return '目标岗位不能为空。'
  if (draft.targetRole.trim().length > 100) return '目标岗位不能超过 100 个字符。'
  if (draft.summary.length > 2000) return '个人简介不能超过 2000 个字符。'
  if (draft.education.length > 4000) return '教育经历不能超过 4000 个字符。'
  if (draft.internship.length > 4000) return '实习经历不能超过 4000 个字符。'
  if (draft.projects.length > 6000) return '项目经历不能超过 6000 个字符。'
  if (draft.skills.length > 2000) return '专业技能不能超过 2000 个字符。'
  return ''
}
