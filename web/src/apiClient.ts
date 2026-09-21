export type ApiErrorResponse = { code?: string; message?: string }

export class ApiRequestError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
  }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

export function notifyUnauthorized() {
  window.dispatchEvent(new Event('atlasmind:unauthorized'))
}

export async function readHttpError(response: Response) {
  const fallback = `HTTP ${response.status}`
  try {
    const body = await response.json() as ApiErrorResponse
    if (body.message) return body.code ? `${body.code}: ${body.message}` : body.message
  } catch {
    // 非 JSON 错误响应使用 HTTP 状态兜底。
  }
  return fallback
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  token?: string
  body?: unknown
  allowEmpty?: boolean
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { token, body, allowEmpty = false, ...requestOptions } = options
  const headers = new Headers(requestOptions.headers)
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let response: Response
  try {
    response = await fetch(path, { ...requestOptions, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ApiRequestError('网络连接失败，请检查服务是否正常运行。', 0)
  }
  if (response.status === 401) notifyUnauthorized()
  if (!response.ok) throw new ApiRequestError(await readHttpError(response), response.status)
  if (response.status === 204) return (allowEmpty ? null : undefined) as T
  const text = await response.text()
  if (allowEmpty && !text.trim()) return null as T
  return JSON.parse(text) as T
}

export async function requestSse(path: string, options: { token: string; body: unknown; signal?: AbortSignal }): Promise<Response> {
  let response: Response
  try {
    response = await fetch(path, {
      method: 'POST',
      signal: options.signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${options.token}`,
      },
      body: JSON.stringify(options.body),
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ApiRequestError('网络连接失败，请检查服务是否正常运行。', 0)
  }
  if (response.status === 401) notifyUnauthorized()
  if (!response.ok) throw new ApiRequestError(await readHttpError(response), response.status)
  return response
}
