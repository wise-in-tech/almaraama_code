import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, TextField } from '@/shared/ui'
import { ZonePicker } from '../components/ZonePicker'

/**
 * The residences of an organization (A1, A9, criterion 1): the list, and the creation of a residence with its address
 * in a commune of the zone tree (or a provisional zone, criterion 3).
 */
export function SitesPage() {
  const { t } = useTranslation()
  const { organizationId } = useParams({ strict: false }) as { organizationId: string }
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [zoneId, setZoneId] = useState('')
  const [line, setLine] = useState('')
  const sites = useQuery({
    queryKey: ['organizations', organizationId, 'sites'],
    queryFn: () => unwrap(api.GET('/v1/organizations/{organizationId}/sites', { params: { path: { organizationId }, query: { size: 100 } } })),
  })
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/organizations/{organizationId}/sites', {
        params: { path: { organizationId } },
        body: { type: 'RESIDENTIAL', name: name.trim(), lifecycleStatus: 'OPERATING', address: { zoneId, addressLine: line.trim() } },
      })),
    onSuccess: () => {
      setName('')
      setLine('')
      void queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'sites'] })
    },
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('sites.title')} />
      {sites.isPending && <Loading />}
      {sites.isError && <ErrorMessage message={problemMessage(sites.error, t)} />}
      {sites.data?.items.length === 0 && <EmptyState message={t('sites.none')} />}
      <ul className="grid gap-2 sm:grid-cols-2">
        {sites.data?.items.map((site) => (
          <li key={site.id}>
            <Link to="/sites/$siteId" params={{ siteId: site.id }} className="block rounded-xl border border-line bg-surface p-4 hover:border-brand">
              <p className="font-semibold">{site.name}</p>
              <p className="text-sm text-muted">{site.address.addressLine}</p>
            </Link>
          </li>
        ))}
      </ul>
      <Card title={t('sites.createTitle')}>
        <TextField id="site-name" label={t('sites.name')} value={name} onChange={(e) => setName(e.target.value)} />
        <ZonePicker organizationId={organizationId} value={zoneId} onChange={setZoneId} />
        <TextField id="site-line" label={t('sites.addressLine')} value={line} onChange={(e) => setLine(e.target.value)} />
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
        <Button tone="primary" busy={create.isPending} disabled={!name.trim() || !zoneId || !line.trim()} onClick={() => create.mutate()}>
          {t('sites.create')}
        </Button>
      </Card>
    </section>
  )
}
