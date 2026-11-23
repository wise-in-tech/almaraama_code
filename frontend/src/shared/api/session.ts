import { setAccessToken } from './access-token'
import { api } from './client'
import type { components } from './schema'

type Session = components['schemas']['Session']

/** Fired when the server no longer accepts the session: the shell sends the person back to sign-in. */
export const SESSION_ENDED_EVENT = 'almaraama:session-ended'

/** Required by the endpoints that read the refresh cookie (CSRF protection, ADR-025). */
const CSRF = { 'X-Almaraama-Csrf': '1' } as const

let renewal: Promise<boolean> | null = null

/** Keeps the access token of a new session in memory and schedules its renewal before it expires. */
export function startSession(session: Session): void {
  setAccessToken(session.accessToken, session.expiresIn)
}

/**
 * Gets a new access token with the HttpOnly refresh cookie. Concurrent callers share one request: the refresh
 * token is single-use, two parallel renewals would look like a theft and end the session.
 */
export function refreshSession(): Promise<boolean> {
  renewal ??= (async () => {
    try {
      const { data } = await api.POST('/v1/auth/refresh', { params: { header: CSRF } })
      if (!data) {
        setAccessToken(null)
        return false
      }
      startSession(data)
      return true
    } catch {
      return false
    } finally {
      renewal = null
    }
  })()
  return renewal
}

/** Signs out on this device: ends the server session, clears the token and tells the application. */
export async function endSession(): Promise<void> {
  try {
    await api.POST('/v1/auth/logout', { params: { header: CSRF } })
  } finally {
    setAccessToken(null)
  }
}
