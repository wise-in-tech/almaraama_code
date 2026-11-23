import { z } from 'zod'

/**
 * Reads the secret of an account link from the URL fragment (`#…`), which browsers never send to a server, then
 * removes it from the address bar and the history (ADR-030).
 */
export function takeTokenFromUrl(): string {
  const token = window.location.hash.replace(/^#/, '')
  if (token) {
    window.history.replaceState(null, '', window.location.pathname)
  }
  return token
}

/** Number of characters as the server counts them (Unicode code points, ADR-030). */
function length(value: string): number {
  return [...value.normalize('NFKC')].length
}

/**
 * A new password and its confirmation (D5, ADR-030): 8 to 128 characters, no composition rule. The server also refuses
 * common passwords and the e-mail itself, and stays the only judge.
 */
export const newPasswordSchema = z
  .object({
    password: z
      .string()
      .refine((value) => length(value) >= 8, 'validation.passwordTooShort')
      .refine((value) => length(value) <= 128, 'validation.passwordTooLong'),
    confirm: z.string(),
  })
  .refine((form) => form.password === form.confirm, { path: ['confirm'], message: 'validation.passwordMismatch' })

/** A new password and its confirmation. */
export type NewPasswordForm = z.infer<typeof newPasswordSchema>

/** An e-mail typed by the person (D1). */
export const emailSchema = z.string().trim().min(1, 'validation.email').email('validation.emailInvalid')
