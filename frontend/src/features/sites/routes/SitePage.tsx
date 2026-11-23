import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Button, Card, ErrorMessage, Loading, PageHeader, Table, TextField } from '@/shared/ui'

/**
 * One residence (A1 to A9, criterion 18): its address, its buildings with their address (a copy of the residence's at
 * creation, or their own), the creation of a building, and the links to its units, QR code, requests and
 * pre-registrations.
 */
export function SitePage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [floors, setFloors] = useState('5')
  const site = useQuery({ queryKey: ['sites', siteId], queryFn: () => unwrap(api.GET('/v1/sites/{siteId}', { params: { path: { siteId } } })) })
  const buildings = useQuery({
    queryKey: ['sites', siteId, 'buildings'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/buildings', { params: { path: { siteId } } })),
  })
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/buildings', {
        params: { path: { siteId } },
        body: { name: name.trim(), buildingUse: 'RESIDENTIAL', floorCount: Number(floors) },
      })),
    onSuccess: () => {
      setName('')
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'buildings'] })
    },
  })
  if (site.isPending) return <Loading />
  if (site.isError) return <ErrorMessage message={problemMessage(site.error, t)} />
  const zone = site.data.address.zone
  const links = [
    { to: '/sites/$siteId/units', label: t('nav.units') },
    { to: '/sites/$siteId/registration-qr', label: t('nav.qr') },
    { to: '/sites/$siteId/memberships', label: t('nav.requests') },
    { to: '/sites/$siteId/pre-registrations', label: t('nav.preRegistrations') },
  ] as const
  return (
    <section className="grid gap-4">
      <PageHeader title={site.data.name}
        intro={`${site.data.address.addressLine} · ${[...zone.ancestors, zone].map((z) => localize(z.name)).join(', ')}`} />
      <nav className="flex flex-wrap gap-2">
        {links.map((link) => (
          <Link key={link.to} to={link.to} params={{ siteId }} className="rounded-lg border border-line bg-surface px-3 py-2 text-sm font-semibold">
            {link.label}
          </Link>
        ))}
      </nav>
      <Card title={t('buildings.title')}>
        {buildings.data && (
          <Table
            headers={[t('buildings.name'), t('buildings.floors'), t('buildings.address')]}
            rows={buildings.data.map((b) => [b.name, String(b.floorCount),
              <span>{b.address.addressLine} <Badge>{t(`addressOrigin.${b.addressOrigin}`)}</Badge></span>])}
          />
        )}
        <div className="grid gap-2 sm:grid-cols-[1fr_8rem_auto] sm:items-end">
          <TextField id="building-name" label={t('buildings.name')} value={name} onChange={(e) => setName(e.target.value)} />
          <TextField id="building-floors" label={t('buildings.floors')} type="number" min={0} max={200} dir="ltr" value={floors}
            onChange={(e) => setFloors(e.target.value)} />
          <Button tone="primary" busy={create.isPending} disabled={!name.trim()} onClick={() => create.mutate()}>{t('buildings.add')}</Button>
        </div>
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
      </Card>
    </section>
  )
}
