/**
 * Where to go after sign-in (criterion 8: a label scanned without session opens the report after sign-in). Only a path
 * of this application is followed: never an absolute URL or a protocol-relative one, which would send the person to
 * another site (open redirect, OWASP ASVS V5.1.5).
 */
export function safeRedirect(target: unknown): string {
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) return '/'
  if (target.startsWith('/login')) return '/'
  return target
}
