import { downloadBlob, toCsv } from '@/shared/download'

/** One key to hand over to a pre-registered person (D3): shown once, never stored by the application. */
export type IssuedKey = { firstName: string; lastName: string; email: string; key: string; expiresAt: string }

/**
 * Downloads the keys of a resident import as CSV, produced in the browser (ADR-027, ADR-034): the manager hands them
 * over "by the means of their choice". The file is the only copy: the server keeps hashes only.
 */
export function downloadKeys(keys: readonly IssuedKey[], headers: readonly string[], fileName: string): void {
  const rows = [headers, ...keys.map((k) => [k.lastName, k.firstName, k.email, k.key, k.expiresAt.slice(0, 10)])]
  downloadBlob(new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' }), fileName)
}
