/**
 * Saves a file produced in the browser (a QR code image, the CSV of the pre-registration keys): nothing leaves for a
 * server (ADR-034).
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

/**
 * A CSV text (RFC 4180, comma separated, UTF-8 with a byte order mark so that Excel reads Arabic correctly). Every
 * cell is quoted; a cell starting with {@code = + - @} is prefixed with a quote, so a spreadsheet never runs it as a
 * formula (CSV injection).
 */
export function toCsv(rows: readonly (readonly string[])[]): string {
  const cell = (value: string) => {
    const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value
    return `"${safe.replaceAll('"', '""')}"`
  }
  return '﻿' + rows.map((row) => row.map(cell).join(',')).join('\r\n') + '\r\n'
}

/** Downloads an API file answer (Excel templates) under this name. */
export async function downloadResponse(response: Response, fileName: string): Promise<void> {
  downloadBlob(await response.blob(), fileName)
}
