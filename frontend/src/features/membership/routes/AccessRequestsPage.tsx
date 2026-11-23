import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { Badge, Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, TextField } from '@/shared/ui'
import type { SiteMembership } from '../api/queries'
import { UnitPicker, type ChosenUnit } from '../components/UnitPicker'

type MembershipStatus = components['schemas']['MembershipStatus']

/**
 * The manager's access requests and residents of a site (D2, K1, E5, criterion 5): each pending request with the
 * person's comment and what their key gave; approval opens the buildings of the chosen units (C7) with the units
 * given as owner or tenant; refusal keeps the request (M2).
 */
export function AccessRequestsPage() {
  const { t } = useTranslation()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const [status, setStatus] = useState<MembershipStatus>('PENDING')
  const memberships = useQuery({
    queryKey: ['sites', siteId, 'memberships', status],
    queryFn: () =>
      unwrap(api.GET('/v1/sites/{siteId}/memberships', { params: { path: { siteId }, query: { status, size: 100 } } })),
  })

  return (
    <section className="grid gap-4">
      <PageHeader title={t('requests.title')} intro={t('requests.intro')} />
      <div role="tablist" className="flex gap-2">
        {(['PENDING', 'ACTIVE', 'REJECTED'] as const).map((value) => (
          <Button key={value} role="tab" aria-selected={status === value} tone={status === value ? 'primary' : 'secondary'}
            onClick={() => setStatus(value)}>
            {t(`requests.tab.${value}`)}
          </Button>
        ))}
      </div>
      {memberships.isPending && <Loading />}
      {memberships.isError && <ErrorMessage message={problemMessage(memberships.error, t)} />}
      {memberships.data?.items.length === 0 && <EmptyState message={t('requests.none')} />}
      {memberships.data?.items.map((membership) =>
        status === 'PENDING' ? (
          <PendingRequest key={membership.id} siteId={siteId} membership={membership} />
        ) : (
          <Card key={membership.id}>
            <p className="font-semibold">{`${membership.person.firstName} ${membership.person.lastName}`}</p>
            {membership.phone && <p dir="ltr" className="text-sm text-muted">{membership.phone}</p>}
            {membership.decisionReason && <p className="text-sm">{membership.decisionReason}</p>}
          </Card>
        ),
      )}
    </section>
  )
}

/** One pending request with its approval and refusal. */
function PendingRequest({ siteId, membership }: { siteId: string; membership: SiteMembership }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [units, setUnits] = useState<ChosenUnit[]>([])
  const [reason, setReason] = useState('')
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'memberships'] })
  const approve = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/v1/site-memberships/{membershipId}/approve', {
          params: { path: { membershipId: membership.id } },
          body: {
            allowedBuildingIds: [],
            occupancies: units.map((u) => ({ unitId: u.unitId, role: u.role, primaryTenant: u.primaryTenant })),
            version: membership.version,
          },
        }),
      ),
    onSuccess: refresh,
  })
  const reject = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/v1/site-memberships/{membershipId}/reject', {
          params: { path: { membershipId: membership.id } },
          body: reason.trim() ? { reason: reason.trim() } : {},
        }),
      ),
    onSuccess: refresh,
  })
  const error = approve.error ?? reject.error

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold">{`${membership.person.firstName} ${membership.person.lastName}`}</p>
        {membership.keyOutcome !== 'NONE' && (
          <Badge tone={membership.keyOutcome === 'NOT_MATCHED' ? 'warn' : 'good'}>
            {t(`keyOutcome.${membership.keyOutcome}`)}
          </Badge>
        )}
      </div>
      {membership.phone && <p dir="ltr" className="text-sm text-muted">{membership.phone}</p>}
      <blockquote className="rounded-lg bg-ground p-3 text-sm">
        {membership.comment ?? t('requests.noComment')}
      </blockquote>
      <UnitPicker siteId={siteId} chosen={units} onChange={setUnits} />
      <ErrorMessage message={error ? problemMessage(error, t) : null} />
      <div className="flex flex-wrap items-end gap-2">
        <Button tone="primary" busy={approve.isPending} onClick={() => approve.mutate()}>
          {t('requests.approve')}
        </Button>
        <div className="flex-1">
          <TextField id={`reject-${membership.id}`} label={t('requests.reason')} value={reason} maxLength={500}
            onChange={(event) => setReason(event.target.value)} />
        </div>
        <Button tone="danger" busy={reject.isPending} onClick={() => reject.mutate()}>
          {t('requests.reject')}
        </Button>
      </div>
    </Card>
  )
}
