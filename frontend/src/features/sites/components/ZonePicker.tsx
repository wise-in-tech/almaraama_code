import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Button, ErrorMessage, SelectField, TextField } from '@/shared/ui'

/**
 * Picks the zone of an address (A8, A9): the upper level first (a wilaya for Algeria), then a zone of the attachment
 * level (a commune). When the commune is missing, the promoteur creates a provisional zone, usable at once, that the
 * platform administrators will validate or merge (criterion 3).
 */
export function ZonePicker({
  organizationId,
  value,
  onChange,
}: {
  organizationId: string
  value: string
  onChange: (zoneId: string) => void
}) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const [parentId, setParentId] = useState('')
  const [missingName, setMissingName] = useState('')
  const upper = useQuery({
    queryKey: ['zones', 'DZ', 'level1'],
    queryFn: () => unwrap(api.GET('/v1/reference/countries/{countryCode}/zones', { params: { path: { countryCode: 'DZ' }, query: { size: 200 } } })),
  })
  const children = useQuery({
    queryKey: ['zones', 'DZ', parentId],
    enabled: parentId !== '',
    queryFn: () =>
      unwrap(api.GET('/v1/reference/countries/{countryCode}/zones', { params: { path: { countryCode: 'DZ' }, query: { parentId, size: 200 } } })),
  })
  const provisional = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/zones/provisional', { body: { organizationId, parentId, name: { ar: missingName, fr: missingName, en: missingName } } })),
    onSuccess: (zone) => {
      void children.refetch()
      onChange(zone.id)
      setMissingName('')
    },
  })
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <SelectField id="zone-upper" label={t('zones.upper')} value={parentId}
        onChange={(event) => {
          setParentId(event.target.value)
          onChange('')
        }}
        options={[{ value: '', label: '—' }, ...(upper.data?.items ?? []).map((z) => ({ value: z.id, label: `${z.code} · ${localize(z.name)}` }))]} />
      <SelectField id="zone-attachment" label={t('zones.attachment')} value={value} disabled={parentId === ''}
        onChange={(event) => onChange(event.target.value)}
        options={[{ value: '', label: '—' }, ...(children.data?.items ?? []).map((z) => ({
          value: z.id,
          label: z.status === 'PROVISIONAL' ? `${localize(z.name)} (${t('zones.provisional')})` : localize(z.name),
        }))]} />
      {parentId !== '' && (
        <div className="grid gap-2 sm:col-span-2">
          <TextField id="zone-missing" label={t('zones.missing')} value={missingName} onChange={(e) => setMissingName(e.target.value)} />
          <Button disabled={missingName.trim().length < 2} busy={provisional.isPending} onClick={() => provisional.mutate()}>
            {t('zones.createProvisional')}
          </Button>
          <ErrorMessage message={provisional.error ? problemMessage(provisional.error, t) : null} />
        </div>
      )}
    </div>
  )
}
