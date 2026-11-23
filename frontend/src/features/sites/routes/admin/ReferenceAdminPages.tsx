import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, SelectField, Table, TextField } from '@/shared/ui'
import { CategoryForm } from '../../components/CategoryForm'

type AdminZone = components['schemas']['AdminZone']

/**
 * Provisional zones waiting for the platform administrators (A8, criterion 3, ADR-035): each one is validated with
 * its official code, or merged into a validated zone of the same level (its addresses move there, history kept).
 */
export function ZonesAdminPage() {
  const { t } = useTranslation()
  const zones = useQuery({
    queryKey: ['admin', 'zones', 'provisional'],
    queryFn: () => unwrap(api.GET('/v1/admin/zones', { params: { query: { countryCode: 'DZ', status: 'PROVISIONAL', size: 100 } } })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('adminZones.title')} intro={t('adminZones.intro')} />
      {zones.isPending && <Loading />}
      {zones.isError && <ErrorMessage message={problemMessage(zones.error, t)} />}
      {zones.data?.items.length === 0 && <EmptyState message={t('adminZones.none')} />}
      {zones.data?.items.map((zone) => <ProvisionalZone key={zone.id} zone={zone} />)}
    </section>
  )
}

/** One provisional zone with its validation and merge. */
function ProvisionalZone({ zone }: { zone: AdminZone }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [target, setTarget] = useState('')
  const parent = zone.ancestors.at(-1)
  const siblings = useQuery({
    queryKey: ['zones', 'DZ', parent?.id ?? 'none'],
    enabled: parent !== undefined,
    queryFn: () =>
      unwrap(api.GET('/v1/reference/countries/{countryCode}/zones', { params: { path: { countryCode: 'DZ' }, query: { parentId: parent?.id ?? '', size: 200 } } })),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'zones'] })
  const validate = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/admin/zones/{zoneId}/validate', { params: { path: { zoneId: zone.id } }, body: { code: code.trim(), version: zone.version } })),
    onSuccess: refresh,
  })
  const merge = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/admin/zones/{zoneId}/merge', { params: { path: { zoneId: zone.id } }, body: { targetZoneId: target, version: zone.version } })),
    onSuccess: refresh,
  })
  return (
    <Card title={localize(zone.name)}>
      <p className="text-sm text-muted">
        {t('adminZones.details', {
          parent: parent ? localize(parent.name) : '—',
          organization: zone.createdByOrganization?.name ?? '—',
          addresses: zone.addressCount,
        })}
      </p>
      <div className="grid gap-2 sm:grid-cols-[1fr_auto] sm:items-end">
        <TextField id={`code-${zone.id}`} label={t('adminZones.code')} dir="ltr" value={code} onChange={(e) => setCode(e.target.value)} />
        <Button tone="primary" busy={validate.isPending} disabled={!code.trim()} onClick={() => validate.mutate()}>{t('adminZones.validate')}</Button>
        <SelectField id={`target-${zone.id}`} label={t('adminZones.target')} value={target} onChange={(e) => setTarget(e.target.value)}
          options={[{ value: '', label: '—' }, ...(siblings.data?.items ?? []).filter((z) => z.status === 'VALIDATED')
            .map((z) => ({ value: z.id, label: `${z.code} · ${localize(z.name)}` }))]} />
        <Button busy={merge.isPending} disabled={!target} onClick={() => {
          if (window.confirm(t('adminZones.mergeConfirm'))) merge.mutate()
        }}>{t('adminZones.merge')}</Button>
      </div>
      <ErrorMessage message={validate.error || merge.error ? problemMessage(validate.error ?? merge.error, t) : null} />
    </Card>
  )
}

/** The default unit categories offered to every organization (A6, M7, criterion 2); our team adds new ones. */
export function UnitCategoriesAdminPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['admin', 'unit-categories'], queryFn: () => unwrap(api.GET('/v1/admin/unit-categories')) })
  const create = useMutation({
    mutationFn: (draft: components['schemas']['PlatformUnitCategoryInput']) => unwrap(api.POST('/v1/admin/unit-categories', { body: draft })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'unit-categories'] }),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('adminCategories.title')} intro={t('adminCategories.intro')} />
      <Card title={t('categories.addTitle')}>
        <CategoryForm busy={create.isPending} onSubmit={(draft) => create.mutate(draft)} />
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
      </Card>
      {categories.data && (
        <Table headers={[t('units.type'), t('categories.code'), t('categories.name')]}
          rows={categories.data.map((c) => [t(`unitType.${c.unitType}`), c.code, localize(c.name)])} />
      )}
    </section>
  )
}
