const STORAGE_KEY = 'almaraama.registrationCode'

/**
 * The registration code of the QR code, read once from the address fragment ({@code /r#K7QM4XPA2R}), which no server
 * sees, then removed from the address bar (ADR-034). Kept for this tab only, so a person who must sign in first finds
 * it again on "my accesses".
 */
export function takeRegistrationCode(): string {
  const fromUrl = window.location.hash.replace(/^#/, '').trim()
  if (fromUrl) {
    window.history.replaceState(null, '', window.location.pathname)
    rememberRegistrationCode(fromUrl)
    return fromUrl
  }
  return rememberedRegistrationCode()
}

/** Keeps the code for this tab; silently skipped when storage is blocked. */
export function rememberRegistrationCode(code: string): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, code)
  } catch {
    // Blocked storage: the person types the code again.
  }
}

/** The code kept for this tab, or an empty string. */
export function rememberedRegistrationCode(): string {
  try {
    return sessionStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

/** Forgets the code once the request is sent. */
export function forgetRegistrationCode(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to forget.
  }
}
