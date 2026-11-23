import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, fileBody, problemMessage, unwrap, type components } from '@/shared/api'
import { downloadBlob } from '@/shared/download'
import { useLocalizedText } from '@/shared/i18n'
import { Button, Card, CheckboxField, ErrorMessage, ImportReportView, Loading, PageHeader, SelectField, Table, TextField } from '@/shared/ui'

type UnitType = components['schemas']['UnitType']
type Language = components['schemas']['Language']
const UNIT_TYPES: UnitType[] = ['APARTMENT', 'SHOP', 'PARKING', 'STORAGE', 'OTHER']

/**
 * The units of a residence (A6, A7, criterion 1): the list with their category, the creation of one unit, and the
 * Excel import of many (ADR-027): template in the person's language, check only or import, report by row and column.
 */
export function UnitsPage() {
  const { t } = useTranslation()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const { localize } = useLocalizedText()
  const units = useQuery({
    queryKey: ['sites', siteId, 'units'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/units', { params: { path: { siteId }, query: { size: 100 } } })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('units.title')} />
      <UnitImport siteId={siteId} />
      <CreateUnit siteId={siteId} />
      {units.isPending && <Loading />}
      {units.isError && <ErrorMessage message={problemMessage(units.error, t)} />}
      {units.data && (
        <Card title={t('units.count', { count: units.data.page.totalElements })}>
          <Table
            headers={[t('units.reference'), t('units.type'), t('units.category'), t('units.floor')]}
            rows={units.data.items.map((u) => [u.reference, t(`unitType.${u.type}`), localize(u.category.name), u.floor?.toString() ?? ''])}
          />
        </Card>
      )}
    </section>
  )
}

/** One unit: type, category of that type (A6), reference, building. */
function CreateUnit({ siteId }: { siteId: string }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const [type, setType] = useState<UnitType>('APARTMENT')
  const [categoryId, setCategoryId] = useState('')
  const [reference, setReference] = useState('')
  const [buildingId, setBuildingId] = useState('')
  const categories = useQuery({
    queryKey: ['unit-categories', siteId, type],
    queryFn: () => unwrap(api.GET('/v1/unit-categories', { params: { query: { siteId, unitType: type } } })),
  })
  const buildings = useQuery({
    queryKey: ['sites', siteId, 'buildings'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/buildings', { params: { path: { siteId } } })),
  })
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/units', {
        params: { path: { siteId } },
        body: { type, categoryId, reference: reference.trim(), saleable: true, rentable: true, ...(buildingId ? { buildingId } : {}) },
      })),
    onSuccess: () => {
      setReference('')
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'units'] })
    },
  })
  return (
    <Card title={t('units.createTitle')}>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField id="unit-type" label={t('units.type')} value={type}
          onChange={(e) => {
            setType(e.target.value as UnitType)
            setCategoryId('')
          }}
          options={UNIT_TYPES.map((value) => ({ value, label: t(`unitType.${value}`) }))} />
        <SelectField id="unit-category" label={t('units.category')} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}
          options={[{ value: '', label: '—' }, ...(categories.data ?? []).map((c) => ({ value: c.id, label: localize(c.name) }))]} />
        <TextField id="unit-reference" label={t('units.reference')} dir="ltr" value={reference} onChange={(e) => setReference(e.target.value)} />
        <SelectField id="unit-building" label={t('buildings.name')} value={buildingId} onChange={(e) => setBuildingId(e.target.value)}
          options={[{ value: '', label: '—' }, ...(buildings.data ?? []).map((b) => ({ value: b.id, label: b.name }))]} />
      </div>
      <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
      <Button tone="primary" busy={create.isPending} disabled={!categoryId || !reference.trim()} onClick={() => create.mutate()}>
        {t('units.create')}
      </Button>
    </Card>
  )
}

/** The Excel import of units (ADR-027, criterion 1). */
function UnitImport({ siteId }: { siteId: string }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [dryRun, setDryRun] = useState(true)
  const template = useMutation({
    mutationFn: async () => {
      const { data, error } = await api.GET('/v1/units/import-template', {
        params: { query: { language: i18n.language.toUpperCase() as Language, siteId } },
        parseAs: 'blob',
      })
      if (error) throw error
      downloadBlob(data as Blob, 'units.xlsx')
    },
  })
  const upload = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/units/import', { params: { path: { siteId }, query: { dryRun } }, ...fileBody(file as File) })),
    onSuccess: (report) => {
      if (report.outcome === 'IMPORTED') void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'units'] })
    },
  })
  return (
    <Card title={t('import.unitsTitle')}>
      <p className="text-sm text-muted">{t('import.unitsIntro')}</p>
      <Button busy={template.isPending} onClick={() => template.mutate()}>{t('import.template')}</Button>
      <input type="file" aria-label={t('import.file')} accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      <CheckboxField id="units-dry-run" label={t('import.dryRun')} checked={dryRun} onChange={(e) => setDryRun(e.target.checked)} />
      <Button tone="primary" busy={upload.isPending} disabled={!file} onClick={() => upload.mutate()}>{t('import.send')}</Button>
      <ErrorMessage message={upload.error || template.error ? problemMessage(upload.error ?? template.error, t) : null} />
      {upload.data && <ImportReportView report={upload.data} />}
    </Card>
  )
}
