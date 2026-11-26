import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { canOnSite, useMyAccess } from '@/features/membership'
import { problemMessage } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Button, Card, EmptyState, ErrorMessage, Loading, PageHeader, SelectField } from '@/shared/ui'
import { useTickets, type TicketSummary, type TicketView } from '../api/queries'
import { categoryTone, OPEN_CATEGORIES } from '../lib/status'

/**
 * The tickets the person may see (H8, C4, M3, criterion 10): a resident's home ("my tickets" and "common parts"), a
 * manager's list with every ticket of their residences. The server applies the visibility (ADR-037); the page only
 * picks the view.
 */
export function TicketsPage() {
  const { t } = useTranslation()
  const access = useMyAccess()
  const [view, setView] = useState<TicketView>('MINE')
  const [siteId, setSiteId] = useState('')
  const [onlyOpen, setOnlyOpen] = useState(true)
  const tickets = useTickets(view, siteId || undefined, onlyOpen ? OPEN_CATEGORIES : undefined)
  const sites = access.data?.sites ?? []
  const canReport = sites.filter((s) => canOnSite(access.data, s.id, 'ticket:create'))
  const manages = sites.some((s) => canOnSite(access.data, s.id, 'ticket:read'))
  return (
    <section className="grid gap-4">
      <PageHeader title={t('tickets.title')} {...(canReport.length === 1 && canReport[0] ? { actions: (
        <Link className="rounded-lg bg-brand px-4 py-2 font-semibold text-surface" to="/sites/$siteId/report" params={{ siteId: canReport[0].id }}>
          {t('tickets.report')}
        </Link>
      ) } : {})} />
      <div className="flex flex-wrap items-end gap-2" role="tablist">
        {(['MINE', 'COMMON', ...(manages ? ['ALL' as const] : [])] as TicketView[]).map((v) => (
          <Button key={v} role="tab" aria-selected={view === v} tone={view === v ? 'primary' : 'secondary'} onClick={() => setView(v)}>
            {t(`tickets.view.${v}`)}
          </Button>
        ))}
        {sites.length > 1 && (
          <SelectField id="tickets-site" label={t('tickets.site')} value={siteId} onChange={(e) => setSiteId(e.target.value)}
            options={[{ value: '', label: t('tickets.allSites') }, ...sites.map((s) => ({ value: s.id, label: s.name }))]} />
        )}
        <Button onClick={() => setOnlyOpen((v) => !v)}>{onlyOpen ? t('tickets.showAll') : t('tickets.showOpen')}</Button>
      </div>
      {canReport.length > 1 && (
        <Card title={t('tickets.report')}>
          <div className="flex flex-wrap gap-2">
            {canReport.map((s) => (
              <Link key={s.id} className="rounded-lg border border-line px-3 py-2 text-sm font-semibold" to="/sites/$siteId/report" params={{ siteId: s.id }}>{s.name}</Link>
            ))}
          </div>
        </Card>
      )}
      {tickets.isPending && <Loading />}
      <ErrorMessage message={tickets.error ? problemMessage(tickets.error, t) : null} />
      {tickets.data && tickets.data.items.length === 0 && <EmptyState message={t('tickets.none')} />}
      <ul className="grid gap-2">
        {tickets.data?.items.map((ticket) => <TicketRow key={ticket.id} ticket={ticket} />)}
      </ul>
    </section>
  )
}

/** One ticket of the list: type, place, step, reference and address of the building (criterion 18). */
export function TicketRow({ ticket }: { ticket: TicketSummary }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const place = [ticket.location.building?.name, ticket.location.unit?.reference, ticket.location.outdoorArea?.name, ticket.asset?.name]
    .filter(Boolean).join(' · ')
  return (
    <li>
      <Link to="/tickets/$ticketId" params={{ ticketId: ticket.id }}
        className="grid gap-1 rounded-lg border border-line bg-surface p-3 hover:border-brand">
        <span className="flex flex-wrap items-center justify-between gap-2">
          <span className="font-semibold">{localize(ticket.issueType.name)}</span>
          <Badge tone={categoryTone(ticket.status.category)}>{localize(ticket.status.label)}</Badge>
        </span>
        <span className="text-sm text-muted">
          <span dir="ltr">{ticket.reference}</span> · {ticket.siteName}{place ? ` · ${place}` : ''}
          {ticket.visibility === 'COMMON' ? ` · ${t('visibility.COMMON')}` : ''}
        </span>
        {ticket.location.address && (
          <span className="text-xs text-muted">{ticket.location.address.addressLine}, {localize(ticket.location.address.zoneName)}</span>
        )}
      </Link>
    </li>
  )
}
