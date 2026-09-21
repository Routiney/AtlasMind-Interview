const STORAGE_KEY = 'atlasmind.auth.v1'
const INACTIVITY_LIMIT_MS = 60 * 60 * 1000

export type AuthSession = {
  accessToken: string
  tokenType: string
  expiresAt: number
  lastSeenAt: number
}

function clearStoredSession() {
  localStorage.removeItem(STORAGE_KEY)
}

export function loadAuthSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null

  try {
    const session = JSON.parse(raw) as AuthSession
    const now = Date.now()
    if (!session.accessToken || session.expiresAt <= now || now - session.lastSeenAt >= INACTIVITY_LIMIT_MS) {
      clearStoredSession()
      return null
    }

    const activeSession = { ...session, lastSeenAt: now }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(activeSession))
    return activeSession
  } catch {
    clearStoredSession()
    return null
  }
}

export function saveAuthSession(input: { accessToken: string; tokenType: string; expiresIn: number }): AuthSession {
  const session: AuthSession = {
    accessToken: input.accessToken,
    tokenType: input.tokenType,
    expiresAt: Date.now() + input.expiresIn * 1000,
    lastSeenAt: Date.now(),
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  return session
}

export function clearAuthSession() {
  clearStoredSession()
}
