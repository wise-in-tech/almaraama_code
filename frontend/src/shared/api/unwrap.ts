/**
 * The data of an openapi-fetch answer, or throws its error body (an RFC 9457 problem): for TanStack Query functions,
 * so that a failed call becomes a failed query or mutation whose error is shown with {@code problemMessage}.
 */
export async function unwrap<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await call
  if (error !== undefined) {
    throw error
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return data as T
}
