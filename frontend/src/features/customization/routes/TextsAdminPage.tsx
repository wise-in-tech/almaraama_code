import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { Badge, Button, Card, CheckboxField, ErrorMessage, Loading, PageHeader, SelectField, TextArea, TextField } from '@/shared/ui'

type Language = components['schemas']['Language']
type TextRow = components['schemas']['TextRow']
const LANGUAGES: Language[] = ['AR', 'EN', 'FR']

/**
 * The dictionary of the interface texts (M6, criterion 20, ADR-029): search by key or text, keys missing in a
 * language, the three languages side by side, right-to-left preview of Arabic, the {@code {{variables}}} each text
 * must keep, history and rollback. A saved text is visible without deployment; a release never overwrites it.
 */
export function TextsAdminPage() {
  const { t } = useTranslation()
  const [q, setQ] = useState('')
  const [missingIn, setMissingIn] = useState<Language | ''>('')
  const [editedOnly, setEditedOnly] = useState(false)
  const rows = useQuery({
    queryKey: ['admin', 'texts', q, missingIn, editedOnly],
    queryFn: () =>
      unwrap(api.GET('/v1/admin/texts', {
        params: { query: { size: 30, editedOnly, ...(q.trim() ? { q: q.trim() } : {}), ...(missingIn ? { missingIn } : {}) } },
      })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('texts.title')} intro={t('texts.intro')} />
      <div className="grid gap-3 sm:grid-cols-3 sm:items-end">
        <TextField id="texts-search" label={t('common.search')} value={q} onChange={(e) => setQ(e.target.value)} />
        <SelectField id="texts-missing" label={t('texts.missingIn')} value={missingIn} onChange={(e) => setMissingIn(e.target.value as Language | '')}
          options={[{ value: '', label: '—' }, ...LANGUAGES.map((value) => ({ value, label: t(`language.${value.toLowerCase()}`) }))]} />
        <CheckboxField id="texts-edited" label={t('texts.editedOnly')} checked={editedOnly} onChange={(e) => setEditedOnly(e.target.checked)} />
      </div>
      {rows.isPending && <Loading />}
      {rows.isError && <ErrorMessage message={problemMessage(rows.error, t)} />}
      {rows.data?.items.map((row) => <TextRowCard key={row.key} row={row} />)}
    </section>
  )
}

/** One key: its text in each language, editable, with its history. */
function TextRowCard({ row }: { row: TextRow }) {
  const { t } = useTranslation()
  const [history, setHistory] = useState(false)
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p dir="ltr" className="font-mono text-sm">{row.key}</p>
        {row.variables && row.variables.length > 0 && (
          <p dir="ltr" className="text-xs text-muted">{row.variables.map((v) => `{{${v}}}`).join(' ')}</p>
        )}
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        {LANGUAGES.map((language) => (
          <LanguageText key={language} textKey={row.key} language={language} value={row.values.find((v) => v.language === language)} />
        ))}
      </div>
      <Button onClick={() => setHistory((shown) => !shown)}>{t('texts.history')}</Button>
      {history && <Revisions textKey={row.key} />}
    </Card>
  )
}

/** The text of one language, saved with the version read. */
function LanguageText({ textKey, language, value }: { textKey: string; language: Language; value: components['schemas']['TextValue'] | undefined }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [text, setText] = useState(value?.text ?? '')
  const save = useMutation({
    mutationFn: () =>
      unwrap(api.PUT('/v1/admin/texts/{key}/{language}', {
        params: { path: { key: textKey, language } },
        body: { text, ...(value ? { version: value.version } : {}) },
      })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'texts'] }),
  })
  return (
    <div className="grid gap-2">
      <TextArea id={`text-${textKey}-${language}`} label={t(`language.${language.toLowerCase()}`)} dir={language === 'AR' ? 'rtl' : 'ltr'}
        lang={language.toLowerCase()} value={text} maxLength={2000} onChange={(e) => setText(e.target.value)} />
      <div className="flex flex-wrap items-center gap-2">
        {value ? <Badge tone={value.origin === 'EDITED' ? 'warn' : 'neutral'}>{t(`textOrigin.${value.origin}`)}</Badge> : <Badge tone="warn">{t('texts.missing')}</Badge>}
        <Button tone="primary" busy={save.isPending} disabled={!text.trim() || text === value?.text} onClick={() => save.mutate()}>
          {t('common.save')}
        </Button>
      </div>
      <ErrorMessage message={save.error ? problemMessage(save.error, t) : null} />
    </div>
  )
}

/** The history of a key, newest first, with rollback (a new revision, M2). */
function Revisions({ textKey }: { textKey: string }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const revisions = useQuery({
    queryKey: ['admin', 'texts', textKey, 'revisions'],
    queryFn: () => unwrap(api.GET('/v1/admin/texts/{key}/revisions', { params: { path: { key: textKey }, query: { size: 20 } } })),
  })
  const restore = useMutation({
    mutationFn: (revisionId: string) => unwrap(api.POST('/v1/admin/text-revisions/{revisionId}/restore', { params: { path: { revisionId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'texts'] }),
  })
  if (revisions.isPending) return <Loading />
  return (
    <ul className="grid gap-2 text-sm">
      {revisions.data?.items.map((revision) => (
        <li key={revision.id} className="flex flex-wrap items-center gap-2 rounded-lg bg-ground p-2">
          <span className="text-muted">{new Date(revision.recordedAt).toLocaleString(i18n.language)}</span>
          <Badge>{revision.language}</Badge>
          <span>{revision.changedBy ? `${revision.changedBy.firstName} ${revision.changedBy.lastName}` : t('texts.release')}</span>
          <span dir={revision.language === 'AR' ? 'rtl' : 'ltr'} className="flex-1">{revision.newText}</span>
          <Button onClick={() => restore.mutate(revision.id)}>{t('texts.restore')}</Button>
        </li>
      ))}
      <ErrorMessage message={restore.error ? problemMessage(restore.error, t) : null} />
    </ul>
  )
}
