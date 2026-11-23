/**
 * The access token lives in memory only, never in localStorage or sessionStorage (ADR-024, ADR-025): a script
 * injected into the page cannot read it back after a reload. The refresh token is an HttpOnly cookie handled by
 * the browser, invisible to JavaScript.
 */
let accessToken: { value: string; expiresAt: number } | null = null

/** The current access token, kept in memory only (never in storage), or null when signed out. */
export function getAccessToken(): string | null {
  return accessToken?.value ?? null
}

/** True when there is no token, or when it expires within the given margin. */
export function accessTokenExpiresWithin(milliseconds: number): boolean {
  return accessToken === null || accessToken.expiresAt - Date.now() < milliseconds
}

/** Stores the access token in memory with its lifetime; null signs out. */
export function setAccessToken(token: string | null, expiresInSeconds = 0): void {
  accessToken = token === null ? null : { value: token, expiresAt: Date.now() + expiresInSeconds * 1000 }
}
