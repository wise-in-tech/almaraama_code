import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { canOnSite, useMyAccess } from '@/features/membership'
import { api, fileBody, problemMessage, unwrap, type components } from '@/shared/api'
import { downloadBlob } from '@/shared/download'
import { useLocalizedText } from '@/shared/i18n'
import { QrCode } from '@/shared/qr'
import { Badge, Button, Card, CheckboxField, ErrorMessage, ImportReportView, Loading, PageHeader, SelectField, Table, TextField } from '@/shared/ui'

type Language = components['schemas']['Language']

/**
 * The equipment of a residence (G1 to G4): the list (resident view for residents), and for its managers the creation,
 * the Excel import (ADR-027 revision 3) and the sheet of QR code labels to print (G4).
 */
export function AssetsPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const access = useMyAccess()
  const [labels, setLabels] = useState(false)
  const assets = useQuery({
    queryKey: ['sites', siteId, 'assets'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/assets', { params: { path: { siteId }, query: { size: 100 } } })),
  })
  const manages = canOnSite(access.data, siteId, 'asset:create')
  const organizationId = access.data?.sites.find((s) => s.id === siteId)?.organizationId
  if (labels && assets.data) {
    return (
      <section className="grid gap-4">
        <div className="flex gap-2 print:hidden">
          <Button tone="primary" onClick={() => window.print()}>{t('assets.print')}</Button>
          <Button onClick={() => setLabels(false)}>{t('report.back')}</Button>
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 print:grid-cols-3">
          {assets.data.items.filter((a) => a.qrCode).map((a) => (
            <figure key={a.id} className="grid justify-items-center gap-1 rounded-lg border border-line p-3 break-inside-avoid">
              <QrCode text={`${window.location.origin}/q/${a.qrCode}`} label={a.name} className="w-36" />
              <figcaption className="text-center text-sm"><strong>{a.name}</strong><br /><span dir="ltr">{a.reference}</span></figcaption>
            </figure>
          ))}
        </div>
      </section>
    )
  }
  return (
    <section className="grid gap-4">
      <PageHeader title={t('assets.title')} actions={manages ? <Button onClick={() => setLabels(true)}>{t('assets.labels')}</Button> : undefined} />
      {manages && organizationId && <CreateAsset siteId={siteId} organizationId={organizationId} />}
      {manages && <AssetImport siteId={siteId} />}
      {assets.isPending && <Loading />}
      <ErrorMessage message={assets.error ? problemMessage(assets.error, t) : null} />
      {assets.data && (
        <Card title={t('assets.count', { count: assets.data.page.totalElements })}>
          <Table
            headers={[t('assets.name'), t('assets.family'), t('assets.reference'), t('assets.place'), t('assets.state')]}
            rows={assets.data.items.map((a) => [
              <Link key={a.id} className="font-semibold underline" to="/assets/$assetId" params={{ assetId: a.id }}>{a.name}</Link>,
              localize(a.family.name),
              <span key="r" dir="ltr">{a.reference}</span>,
              [a.building?.name, a.floor !== undefined ? t('tickets.floor', { floor: a.floor }) : null, a.outdoorArea?.name].filter(Boolean).join(' · '),
              <Badge key="s" tone={a.state === 'IN_SERVICE' ? 'good' : 'warn'}>{t(`assetState.${a.state}`)}</Badge>,
            ])}
          />
        </Card>
      )}
    </section>
  )
}

/** One equipment: family and model of the organization, name, place in the residence (G1, G2). */
function CreateAsset({ siteId, organizationId }: { siteId: string; organizationId: string }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const [familyId, setFamilyId] = useState('')
  const [modelId, setModelId] = useState('')
  const [name, setName] = useState('')
  const [buildingId, setBuildingId] = useState('')
  const [floor, setFloor] = useState('')
  const families = useQuery({
    queryKey: ['organizations', organizationId, 'asset-families'],
    queryFn: () => unwrap(api.GET('/v1/organizations/{organizationId}/asset-families', { params: { path: { organizationId } } })),
  })
  const buildings = useQuery({
    queryKey: ['sites', siteId, 'buildings'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/buildings', { params: { path: { siteId } } })),
  })
  const models = families.data?.find((f) => f.id === familyId)?.models ?? []
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/assets', {
        params: { path: { siteId } },
        body: { familyId, name: name.trim(), ...(modelId ? { modelId } : {}), ...(buildingId ? { buildingId } : {}),
          ...(buildingId && floor !== '' ? { floor: Number(floor) } : {}) },
      })),
    onSuccess: () => {
      setName('')
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'assets'] })
    },
  })
  return (
    <Card title={t('assets.createTitle')}>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField id="asset-family" label={t('assets.family')} value={familyId} onChange={(e) => { setFamilyId(e.target.value); setModelId('') }}
          options={[{ value: '', label: '—' }, ...(families.data ?? []).map((f) => ({ value: f.id, label: localize(f.name) }))]} />
        <SelectField id="asset-model" label={t('assets.model')} value={modelId} onChange={(e) => setModelId(e.target.value)}
          options={[{ value: '', label: '—' }, ...models.map((m) => ({ value: m.id, label: m.modelVersion ? `${m.name} ${m.modelVersion}` : m.name }))]} />
        <TextField id="asset-name" label={t('assets.name')} value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
        <SelectField id="asset-building" label={t('buildings.name')} value={buildingId} onChange={(e) => setBuildingId(e.target.value)}
          options={[{ value: '', label: '—' }, ...(buildings.data ?? []).map((b) => ({ value: b.id, label: b.name }))]} />
        {buildingId && <TextField id="asset-floor" label={t('units.floor')} type="number" inputMode="numeric" value={floor} onChange={(e) => setFloor(e.target.value)} />}
      </div>
      <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
      <Button tone="primary" busy={create.isPending} disabled={!familyId || !name.trim()} onClick={() => create.mutate()}>{t('assets.create')}</Button>
    </Card>
  )
}

/** The Excel import of equipment (ADR-027 revision 3): template, check only or import, report by row and column. */
function AssetImport({ siteId }: { siteId: string }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [dryRun, setDryRun] = useState(true)
  const template = useMutation({
    mutationFn: async () => {
      const { data, error } = await api.GET('/v1/assets/import-template', {
        params: { query: { language: i18n.language.toUpperCase() as Language, siteId } },
        parseAs: 'blob',
      })
      if (error) throw error
      downloadBlob(data as Blob, 'equipment.xlsx')
    },
  })
  const upload = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/assets/import', { params: { path: { siteId }, query: { dryRun } }, ...fileBody(file as File) })),
    onSuccess: (report) => {
      if (report.outcome === 'IMPORTED') void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'assets'] })
    },
  })
  return (
    <Card title={t('assets.importTitle')}>
      <p className="text-sm text-muted">{t('assets.importIntro')}</p>
      <Button busy={template.isPending} onClick={() => template.mutate()}>{t('import.template')}</Button>
      <input type="file" aria-label={t('import.file')} accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      <CheckboxField id="assets-dry-run" label={t('import.dryRun')} checked={dryRun} onChange={(e) => setDryRun(e.target.checked)} />
      <Button tone="primary" busy={upload.isPending} disabled={!file} onClick={() => upload.mutate()}>{t('import.send')}</Button>
      <ErrorMessage message={upload.error || template.error ? problemMessage(upload.error ?? template.error, t) : null} />
      {upload.data && <ImportReportView report={upload.data} />}
    </Card>
  )
}
