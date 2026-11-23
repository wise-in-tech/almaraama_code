/** Header carrying the idempotency key of a command, stored by the backend in `idempotency_key`. */
export const IDEMPOTENCY_HEADER = 'Idempotency-Key'

/**
 * A new key per user action. A command replayed from the offline queue keeps its key, so the backend applies it
 * once only, however many times it is sent.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
