/**
 * The body options of an openapi-fetch call that sends one file as {@code multipart/form-data} (Excel imports,
 * ADR-027): the browser sets the boundary; the bytes go as they are, nothing is read or kept by the page.
 */
export function fileBody(file: File): { body: { file: string }; bodySerializer: (body: unknown) => FormData } {
  const form = new FormData()
  form.append('file', file)
  return { body: form as unknown as { file: string }, bodySerializer: (body) => body as FormData }
}

/**
 * The body options of an openapi-fetch call that sends one file with text fields as {@code multipart/form-data}
 * (photos and documents, ADR-038: {@code purpose}).
 */
export function fileWithFields<F extends Record<string, string>>(
  file: File,
  fields: F,
): { body: { file: string } & F; bodySerializer: (body: unknown) => FormData } {
  const form = new FormData()
  form.append('file', file)
  for (const [key, value] of Object.entries(fields)) form.append(key, value)
  return { body: form as unknown as { file: string } & F, bodySerializer: (body) => body as FormData }
}
