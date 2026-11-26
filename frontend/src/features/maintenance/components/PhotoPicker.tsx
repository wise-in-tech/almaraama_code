import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { problemMessage } from '@/shared/api'
import { ErrorMessage } from '@/shared/ui'
import { uploadTicketPhoto } from '../api/queries'

/**
 * Up to three photos for a ticket (H10): each is shrunk on the phone and uploaded at once, so that the ticket only
 * sends their identifiers. {@code onChange} receives the identifiers of the photos ready.
 */
export function PhotoPicker({ max, onChange }: { max: number; onChange: (fileIds: string[]) => void }) {
  const { t } = useTranslation()
  const [ready, setReady] = useState<{ id: string; name: string }[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  /** Uploads the chosen files that still fit under the limit. */
  async function add(files: FileList | null) {
    if (!files) return
    setBusy(true)
    setError(null)
    const next = [...ready]
    try {
      for (const file of Array.from(files).slice(0, max - next.length)) {
        next.push({ id: await uploadTicketPhoto(file), name: file.name })
      }
    } catch (e) {
      setError(e)
    } finally {
      setReady(next)
      onChange(next.map((p) => p.id))
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-2">
      <label className="grid gap-1 text-sm font-medium">
        {t('tickets.photos', { count: ready.length, max })}
        <input type="file" accept="image/*" capture="environment" multiple disabled={busy || ready.length >= max}
          onChange={(event) => void add(event.target.files)} />
      </label>
      {busy && <p className="text-sm text-muted">{t('tickets.photoSending')}</p>}
      {ready.length > 0 && <ul className="text-sm text-muted">{ready.map((p) => <li key={p.id}>{p.name}</li>)}</ul>}
      <ErrorMessage message={error ? problemMessage(error, t) : null} />
    </div>
  )
}
