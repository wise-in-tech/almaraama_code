import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams, useSearch } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMyMemberships } from '@/features/membership'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Button, Card, ErrorMessage, Loading, PageHeader, SelectField, TextArea } from '@/shared/ui'
import { ticketKeys, useIssueCatalog, type IssueCatalogCategory } from '../api/queries'
import { PhotoPicker } from '../components/PhotoPicker'

type Chosen = { id: string; assetRequired: boolean; visibility: 'COMMON' | 'PRIVATE' }

/**
 * Report a problem in three screens at most (H1 to H4, H10, G4, criterion 8): 1. what is wrong (category then type,
 * filtered by the equipment of a QR code), 2. details and photos, 3. sent. Opened from the home page or from the QR
 * code of an equipment ({@code ?assetId=}), which fills the place.
 */
export function ReportTicketPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const { assetId } = useSearch({ strict: false }) as { assetId?: string }
  const catalog = useIssueCatalog(siteId, assetId)
  const asset = useQuery({
    queryKey: ['assets', assetId],
    enabled: Boolean(assetId),
    queryFn: () => unwrap(api.GET('/v1/assets/{assetId}', { params: { path: { assetId: assetId as string } } })),
  })
  const [category, setCategory] = useState<IssueCatalogCategory | null>(null)
  const [chosen, setChosen] = useState<Chosen | null>(null)
  const [sent, setSent] = useState<{ id: string; reference: string } | null>(null)

  if (sent) {
    return (
      <section className="grid gap-4">
        <PageHeader title={t('report.sentTitle')} intro={t('report.sentIntro', { reference: sent.reference })} />
        <div className="flex flex-wrap gap-2">
          <Link className="rounded-lg bg-brand px-4 py-2 font-semibold text-surface" to="/tickets/$ticketId" params={{ ticketId: sent.id }}>
            {t('report.see')}
          </Link>
          <Link className="rounded-lg border border-line px-4 py-2 font-semibold" to="/tickets">{t('report.myTickets')}</Link>
        </div>
      </section>
    )
  }
  return (
    <section className="grid gap-4">
      <PageHeader title={t('report.title')} {...(asset.data ? { intro: t('report.forAsset', { name: asset.data.name }) } : {})} />
      {catalog.isPending && <Loading />}
      <ErrorMessage message={catalog.error ? problemMessage(catalog.error, t) : null} />
      {!chosen && catalog.data && (
        <Card title={t('report.whatTitle')}>
          {!category ? (
            <div className="grid gap-2 sm:grid-cols-2">
              {catalog.data.map((c) => (
                <Button key={c.id} className="min-h-14 text-base" onClick={() => setCategory(c)}>{localize(c.name)}</Button>
              ))}
            </div>
          ) : (
            <div className="grid gap-2">
              <p className="text-sm text-muted">{localize(category.name)}</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {category.types.map((type) => (
                  <Button key={type.id} className="min-h-14 text-base" disabled={type.assetRequired && !assetId}
                    onClick={() => setChosen({ id: type.id, assetRequired: type.assetRequired, visibility: type.visibility })}>
                    {localize(type.name)}
                  </Button>
                ))}
              </div>
              {category.types.some((type) => type.assetRequired) && !assetId && <p className="text-sm text-muted">{t('report.scanForTechnical')}</p>}
              <Button onClick={() => setCategory(null)}>{t('report.back')}</Button>
            </div>
          )}
        </Card>
      )}
      {chosen && (
        <Details siteId={siteId} {...(assetId ? { assetId } : {})} chosen={chosen} onBack={() => setChosen(null)} onSent={setSent} />
      )}
    </section>
  )
}

/** Screen 2: description, photos and, without an equipment, the place (a unit of mine or a building). */
function Details({ siteId, assetId, chosen, onBack, onSent }: {
  siteId: string
  assetId?: string
  chosen: Chosen
  onBack: () => void
  onSent: (ticket: { id: string; reference: string }) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const memberships = useMyMemberships()
  const [description, setDescription] = useState('')
  const [photoFileIds, setPhotoFileIds] = useState<string[]>([])
  const [unitId, setUnitId] = useState('')
  const [buildingId, setBuildingId] = useState('')
  const buildings = useQuery({
    queryKey: ['sites', siteId, 'buildings'],
    enabled: !assetId,
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/buildings', { params: { path: { siteId } } })),
  })
  const myUnits = (memberships.data ?? []).filter((m) => m.siteId === siteId).flatMap((m) => m.occupancies ?? [])
  const send = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/tickets', {
        body: {
          siteId,
          issueTypeId: chosen.id,
          ...(assetId ? { assetId } : {}),
          ...(!assetId && unitId ? { unitId } : {}),
          ...(!assetId && !unitId && buildingId ? { buildingId } : {}),
          ...(description.trim() ? { description: description.trim() } : {}),
          photoFileIds,
        },
      })),
    onSuccess: (ticket) => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      onSent({ id: ticket.id, reference: ticket.reference })
    },
  })
  return (
    <Card title={t('report.detailsTitle')}>
      <Badge tone={chosen.visibility === 'COMMON' ? 'neutral' : 'warn'}>{t(`visibility.${chosen.visibility}`)}</Badge>
      {!assetId && (
        <div className="grid gap-3 sm:grid-cols-2">
          {myUnits.length > 0 && (
            <SelectField id="report-unit" label={t('report.unit')} value={unitId} onChange={(e) => setUnitId(e.target.value)}
              options={[{ value: '', label: t('report.commonParts') }, ...myUnits.map((o) => ({ value: o.unitId, label: o.unitReference }))]} />
          )}
          {!unitId && (
            <SelectField id="report-building" label={t('buildings.name')} value={buildingId} onChange={(e) => setBuildingId(e.target.value)}
              options={[{ value: '', label: '—' }, ...(buildings.data ?? []).map((b) => ({ value: b.id, label: b.name }))]} />
          )}
        </div>
      )}
      <TextArea id="report-description" label={t('report.description')} maxLength={2000} rows={4} value={description}
        onChange={(e) => setDescription(e.target.value)} />
      <PhotoPicker max={3} onChange={setPhotoFileIds} />
      <ErrorMessage message={send.error ? problemMessage(send.error, t) : null} />
      <div className="flex flex-wrap gap-2">
        <Button tone="primary" className="min-h-14 text-base" busy={send.isPending} onClick={() => send.mutate()}>{t('report.send')}</Button>
        <Button onClick={onBack}>{t('report.back')}</Button>
      </div>
    </Card>
  )
}
