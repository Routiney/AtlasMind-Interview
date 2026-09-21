import { requestJson } from './apiClient'

export { readHttpError } from './apiClient'
export type LoginResponse = { accessToken: string; tokenType: string; expiresIn: number }
export type CurrentUser = { userId: string; username: string }
export type RegisterRequest = { username: string; password: string; displayName: string }
export type RegisterResponse = { id: number; username: string; displayName: string; enabled: boolean; createdAt: string }
export type ResumeProfile = { id: number; userId: number; name: string; targetRole: string; summary: string; education: string; internship: string; projects: string; skills: string; createdAt: string; updatedAt: string }
export type Conversation = { id: number; title: string; createdAt: string; updatedAt: string }
export type ConversationPage = { items: Conversation[]; page: number; size: number; hasNext: boolean }
export type ConversationMessage = { id: number; role: 'USER' | 'ASSISTANT' | 'SYSTEM'; content: string; createdAt: string }
export type MessagePage = { items: ConversationMessage[]; page: number; size: number; hasNext: boolean }

export async function login(username: string, password: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>('/api/auth/login', { method: 'POST', body: { username, password } })
}

export async function devLogin(): Promise<LoginResponse> {
  return requestJson<LoginResponse>('/api/auth/dev-login', { method: 'POST' })
}

export async function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return requestJson<CurrentUser>('/api/auth/me', { token: accessToken })
}

export async function register(input: RegisterRequest): Promise<RegisterResponse> {
  return requestJson<RegisterResponse>('/api/users', { method: 'POST', body: input })
}

export async function getResume(accessToken: string, signal?: AbortSignal): Promise<ResumeProfile | null> {
  return requestJson<ResumeProfile | null>('/api/resume', { token: accessToken, signal, allowEmpty: true })
}

export async function saveResume(accessToken: string, input: Omit<ResumeProfile, 'id' | 'userId' | 'createdAt' | 'updatedAt'>): Promise<ResumeProfile> {
  return requestJson<ResumeProfile>('/api/resume', { method: 'PUT', token: accessToken, body: input })
}

export async function getConversations(accessToken: string, page = 0, size = 20, signal?: AbortSignal): Promise<ConversationPage> {
  return requestJson<ConversationPage>(`/api/conversations?page=${page}&size=${size}`, { token: accessToken, signal })
}

export async function createConversation(accessToken: string, title = '新建对话'): Promise<Conversation> {
  return requestJson<Conversation>('/api/conversations', { method: 'POST', token: accessToken, body: { title } })
}

export async function updateConversationTitle(accessToken: string, conversationId: number, title: string): Promise<Conversation> {
  return requestJson<Conversation>(`/api/conversations/${conversationId}`, { method: 'PATCH', token: accessToken, body: { title } })
}

export async function deleteConversation(accessToken: string, conversationId: number): Promise<void> {
  await requestJson<void>(`/api/conversations/${conversationId}`, { method: 'DELETE', token: accessToken })
}

export async function getConversationMessages(accessToken: string, conversationId: number, page = 0, size = 30, signal?: AbortSignal): Promise<MessagePage> {
  return requestJson<MessagePage>(`/api/conversations/${conversationId}/messages?page=${page}&size=${size}`, { token: accessToken, signal })
}
