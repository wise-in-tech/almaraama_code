import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { coreBlocks, createRegistry, ScreenRenderer } from '@/engine'
import { useMyAccess } from '@/features/membership'
import { api, fileWithFields, problemMessage, unwrap, type components } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { shrinkPhoto } from '@/shared/photo'
import { QrCode } from '@/shared/qr'
import { Badge, Button, Card, ErrorMessage, Loading, PageHeader, SelectField, TextField } from '@/shared/ui'
import { characteristicsScreen, valuesFromForm } from '../lib/characteristics'

type Asset = components['schemas']['Asset']
type Definition = components['schemas']['AttributeDefinition']
type DocumentType = components['schemas']['AssetDocumentType']

/** The closed registry of the equipment screens: the engine's blocks only (ADR-024). */
const registry = createRegistry(coreBlocks)

/**
 * One equipment (G1 to G4): what everyone sees (name, family, place, state, "report a problem"); for its managers the
 * "Caractéristiques" block rendered by the screen engine and its form, documents, warranties, QR code and archive.
 */
export function AssetPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { assetId } = useParams({ strict: false }) as { assetId: string }
  const asset = useQuery({
    queryKey: ['assets', assetId],
    queryFn: () => unwrap(api.GET('/v1/assets/{assetId}', { params: { path: { assetId } } })),
  })
  if (asset.isPending) return <Loading />
  if (asset.error) return <ErrorMessage message={problemMessage(asset.error, t)} />
  const data = asset.data
  return (
    <section className="grid gap-4">
      <PageHeader title={data.name} intro={`${localize(data.family.name)} · ${data.reference} · ${data.siteName}`} />
      <Card>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={data.state === 'IN_SERVICE' ? 'good' : 'warn'}>{t(`assetState.${data.state}`)}</Badge>
          {data.archived && <Badge>{t('assets.archived')}</Badge>}
          <span className="text-sm text-muted">
            {[data.building?.name, data.floor !== undefined ? t('tickets.floor', { floor: data.floor }) : null, data.outdoorArea?.name].filter(Boolean).join(' · ')}
          </span>
        </div>
        {!data.archived && (
          <Link className="w-fit rounded-lg bg-brand px-4 py-2 font-semibold text-surface" to="/sites/$siteId/report"
            params={{ siteId: data.siteId }} search={{ assetId: data.id }}>{t('assets.reportProblem')}</Link>
        )}
      </Card>
      {data.management && <Management asset={data} />}
    </section>
  )
}

/** The management view: characteristics, documents, warranties, QR code (asset:update). */
function Management({ asset }: { asset: Asset }) {
  const { t } = useTranslation()
  const access = useMyAccess()
  const queryClient = useQueryClient()
  const organizationId = access.data?.sites.find((s) => s.id === asset.siteId)?.organizationId
  const definitions = useQuery({
    queryKey: ['asset-families', asset.family.id, 'attributes', organizationId],
    enabled: Boolean(organizationId),
    queryFn: () =>
      unwrap(api.GET('/v1/asset-families/{familyId}/attributes', {
        params: { path: { familyId: asset.family.id }, query: { organizationId: organizationId as string } },
      })),
  })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['assets', asset.id] })
  const rotate = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/assets/{assetId}/qr-code/rotate', { params: { path: { assetId: asset.id } } })),
    onSuccess: refresh,
  })
  const archive = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/assets/{assetId}/archive', { params: { path: { assetId: asset.id } }, body: { version: asset.version } })),
    onSuccess: refresh,
  })
  const management = asset.management as NonNullable<Asset['management']>
  return (
    <>
      {definitions.data && <Characteristics asset={asset} definitions={definitions.data} />}
      <Card title={t('assets.documents')}>
        <ul className="grid gap-1 text-sm">
          {management.documents.map((d) => (
            <li key={d.id}><a className="underline" href={d.file.url} target="_blank" rel="noopener noreferrer">{d.title}</a> · {t(`documentType.${d.type}`)}</li>
          ))}
        </ul>
        {!asset.archived && <AddDocument assetId={asset.id} onDone={refresh} />}
      </Card>
      <Card title={t('assets.warranties')}>
        <ul className="grid gap-1 text-sm">
          {management.warranties.map((w) => <li key={w.id} dir="ltr">{w.startDate} → {w.endDate}</li>)}
        </ul>
        {!asset.archived && <AddWarranty assetId={asset.id} onDone={refresh} />}
      </Card>
      {asset.qrCode && (
        <Card title={t('assets.qrTitle')}>
          <QrCode text={`${window.location.origin}/q/${asset.qrCode}`} label={asset.name} className="w-48" />
          <div className="flex flex-wrap gap-2 print:hidden">
            <Button onClick={() => window.print()}>{t('assets.print')}</Button>
            <Button busy={rotate.isPending} onClick={() => rotate.mutate()}>{t('assets.rotateQr')}</Button>
            <Button tone="danger" busy={archive.isPending} onClick={() => archive.mutate()}>{t('assets.archive')}</Button>
          </div>
          <ErrorMessage message={rotate.error || archive.error ? problemMessage(rotate.error ?? archive.error, t) : null} />
        </Card>
      )}
    </>
  )
}

/** G3: the characteristics shown by the engine's FieldGroup block, and their form generated from the definitions. */
function Characteristics({ asset, definitions }: { asset: Asset; definitions: Definition[] }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const values = asset.management?.attributes ?? {}
  const [editing, setEditing] = useState(false)
  const [typed, setTyped] = useState<Record<string, string>>(() =>
    Object.fromEntries(Object.entries(values).map(([k, v]) => [k, String(v)])))
  const word = (key: string) => ({ ar: t(key, { lng: 'ar' }), en: t(key, { lng: 'en' }), fr: t(key, { lng: 'fr' }) })
  const screen = useMemo(() => characteristicsScreen(word('assets.characteristics'), definitions, values,
    { yes: word('common.yes'), no: word('common.no') }), [definitions, values]) // eslint-disable-line react-hooks/exhaustive-deps
  const save = useMutation({
    mutationFn: () =>
      unwrap(api.PUT('/v1/assets/{assetId}', {
        params: { path: { assetId: asset.id } },
        body: {
          name: asset.name, reference: asset.reference, state: asset.state, version: asset.version,
          ...(asset.model ? { modelId: asset.model.id } : {}), ...(asset.building ? { buildingId: asset.building.id } : {}),
          ...(asset.floor !== undefined ? { floor: asset.floor } : {}), ...(asset.outdoorArea ? { outdoorAreaId: asset.outdoorArea.id } : {}),
          ...(asset.management?.supplier ? { supplierId: asset.management.supplier.id } : {}),
          attributes: valuesFromForm(definitions, typed),
        },
      })),
    onSuccess: () => {
      setEditing(false)
      void queryClient.invalidateQueries({ queryKey: ['assets', asset.id] })
    },
  })
  if (definitions.length === 0) return null
  return (
    <div className="grid gap-2">
      {!editing && <ScreenRenderer definition={screen} registry={registry} />}
      {editing && (
        <Card title={t('assets.characteristics')}>
          <div className="grid gap-3 sm:grid-cols-2">
            {definitions.filter((d) => d.active).map((d) => d.dataType === 'LIST' || d.dataType === 'BOOLEAN' ? (
              <SelectField key={d.code} id={`attr-${d.code}`} label={localize(d.label)} value={typed[d.code] ?? ''}
                onChange={(e) => setTyped((v) => ({ ...v, [d.code]: e.target.value }))}
                options={[{ value: '', label: '—' }, ...(d.dataType === 'BOOLEAN'
                  ? [{ value: 'true', label: t('common.yes') }, { value: 'false', label: t('common.no') }]
                  : (d.options ?? []).filter((o) => o.active).map((o) => ({ value: o.code, label: localize(o.label) })))]} />
            ) : (
              <TextField key={d.code} id={`attr-${d.code}`} label={d.unit ? `${localize(d.label)} (${d.unit})` : localize(d.label)}
                type={d.dataType === 'DATE' ? 'date' : 'text'} inputMode={d.dataType === 'INTEGER' || d.dataType === 'DECIMAL' ? 'decimal' : undefined}
                required={d.required} value={typed[d.code] ?? ''} onChange={(e) => setTyped((v) => ({ ...v, [d.code]: e.target.value }))} />
            ))}
          </div>
          <ErrorMessage message={save.error ? problemMessage(save.error, t) : null} />
          <div className="flex gap-2">
            <Button tone="primary" busy={save.isPending} onClick={() => save.mutate()}>{t('common.save')}</Button>
            <Button onClick={() => setEditing(false)}>{t('tickets.cancelForm')}</Button>
          </div>
        </Card>
      )}
      {!editing && !asset.archived && <Button className="w-fit" onClick={() => setEditing(true)}>{t('assets.editCharacteristics')}</Button>}
    </div>
  )
}

/** G3: a document (PDF or photo), uploaded then attached. */
function AddDocument({ assetId, onDone }: { assetId: string; onDone: () => void }) {
  const { t } = useTranslation()
  const [title, setTitle] = useState('')
  const [type, setType] = useState<DocumentType>('MANUAL')
  const [file, setFile] = useState<File | null>(null)
  const add = useMutation({
    mutationFn: async () => {
      const prepared = await shrinkPhoto(file as File)
      const uploaded = await unwrap(api.POST('/v1/files', fileWithFields(prepared, { purpose: 'ASSET_DOCUMENT' as const })))
      return unwrap(api.POST('/v1/assets/{assetId}/documents', { params: { path: { assetId } }, body: { fileId: uploaded.id, type, title: title.trim() } }))
    },
    onSuccess: () => {
      setTitle('')
      onDone()
    },
  })
  return (
    <div className="grid gap-2 sm:grid-cols-3">
      <TextField id="doc-title" label={t('assets.documentTitle')} value={title} maxLength={120} onChange={(e) => setTitle(e.target.value)} />
      <SelectField id="doc-type" label={t('assets.documentType')} value={type} onChange={(e) => setType(e.target.value as DocumentType)}
        options={(['MANUAL', 'CERTIFICATE', 'INVOICE', 'PHOTO', 'OTHER'] as const).map((value) => ({ value, label: t(`documentType.${value}`) }))} />
      <input type="file" aria-label={t('import.file')} accept="application/pdf,image/*" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      <ErrorMessage message={add.error ? problemMessage(add.error, t) : null} />
      <Button busy={add.isPending} disabled={!file || !title.trim()} onClick={() => add.mutate()}>{t('assets.addDocument')}</Button>
    </div>
  )
}

/** G3: a warranty with its dates; the alert before its end comes in sprint 4 (K4). */
function AddWarranty({ assetId, onDone }: { assetId: string; onDone: () => void }) {
  const { t } = useTranslation()
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const add = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/assets/{assetId}/warranties', { params: { path: { assetId } }, body: { startDate: start, endDate: end, alertDaysBefore: 30 } })),
    onSuccess: () => {
      setStart('')
      setEnd('')
      onDone()
    },
  })
  return (
    <div className="grid gap-2 sm:grid-cols-3">
      <TextField id="warranty-start" type="date" label={t('assets.warrantyStart')} value={start} onChange={(e) => setStart(e.target.value)} />
      <TextField id="warranty-end" type="date" label={t('assets.warrantyEnd')} value={end} onChange={(e) => setEnd(e.target.value)} />
      <ErrorMessage message={add.error ? problemMessage(add.error, t) : null} />
      <Button busy={add.isPending} disabled={!start || !end} onClick={() => add.mutate()}>{t('assets.addWarranty')}</Button>
    </div>
  )
}
