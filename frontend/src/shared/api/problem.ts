import type { TFunction } from 'i18next'
import type { components } from './schema'

/** Error response of the API (RFC 9457), as defined in api/openapi.yaml. */
export type Problem = components['schemas']['Problem']

/** Whether an error body is an RFC 9457 problem with a stable code. */
export function isProblem(value: unknown): value is Problem {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Partial<Problem>).code === 'string' &&
    typeof (value as Partial<Problem>).status === 'number'
  )
}

/**
 * Translation keys for a problem, most specific first: the stable English error code, then a generic message.
 * The server message itself is never shown to the user.
 */
export function problemMessageKeys(problem: Problem): string[] {
  return [`errors.codes.${problem.code}`, 'errors.generic']
}

/** The message to show for an error returned by the API, in the current language. */
export function problemMessage(error: unknown, t: TFunction): string {
  if (!isProblem(error)) {
    return t('errors.generic')
  }
  const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 60) / 60))
  return t(problemMessageKeys(error), { minutes })
}
