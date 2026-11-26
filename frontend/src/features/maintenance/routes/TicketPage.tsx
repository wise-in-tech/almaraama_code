import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Button, Card, ErrorMessage, Loading, PageHeader, SelectField, TextArea } from '@/shared/ui'
import { ticketKeys, uploadTicketPhoto, useTicket, type AvailableTransition, type Ticket } from '../api/queries'
import { categoryTone, missingInput } from '../lib/status'

/**
 * One ticket (H5 to H10): its place, step, photos and technicians, what the person may do now (the passages the
 * server offers, with their reason or comment), and the whole history. A common ticket seen as a member is read only
 * and shows no requester, photo or name (ADR-037).
 */
export function TicketPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { ticketId } = useParams({ strict: false }) as { ticketId: string }
  const ticket = useTicket(ticketId)
  if (ticket.isPending) return <Loading />
  if (ticket.error) return <ErrorMessage message={problemMessage(ticket.error, t)} />
  const data = ticket.data
  return (
    <section className="grid gap-4">
      <PageHeader title={localize(data.issueType.name)} intro={`${data.reference} · ${data.siteName}`} />
      <Card>
        <div className="flex flex-wrap gap-2">
          <Badge tone={categoryTone(data.status.category)}>{localize(data.status.label)}</Badge>
          <Badge>{t(`visibility.${data.visibility}`)}</Badge>
          {data.status.holdReason && <Badge tone="warn">{t(`holdReason.${data.status.holdReason}`)}</Badge>}
        </div>
        <Place ticket={data} />
        {data.description && <p className="whitespace-pre-line">{data.description}</p>}
        {data.requester && <p className="text-sm text-muted">{t('tickets.requester', { name: `${data.requester.firstName} ${data.requester.lastName}` })}</p>}
        {data.technicians.length > 0 && (
          <p className="text-sm text-muted">{t('tickets.technicians', { names: data.technicians.map((p) => `${p.firstName} ${p.lastName}`).join(', ') })}</p>
        )}
        {data.photos.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {data.photos.map((photo) => (
              <img key={photo.id} src={photo.file.url} alt={t('tickets.photoAlt')} className="h-32 w-32 rounded-lg object-cover" loading="lazy" />
            ))}
          </div>
        )}
        {data.canAddPhoto && <AddPhoto ticketId={ticketId} />}
      </Card>
      <Actions ticket={data} />
      <History ticketId={ticketId} />
    </section>
  )
}

/** Where the problem is, with the address of the building (criterion 18). */
function Place({ ticket }: { ticket: Ticket }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const parts = [
    ticket.location.building?.name,
    ticket.location.floor !== undefined ? t('tickets.floor', { floor: ticket.location.floor }) : null,
    ticket.location.unit?.reference,
    ticket.location.outdoorArea?.name,
    ticket.asset ? `${ticket.asset.name} (${ticket.asset.reference})` : null,
  ].filter(Boolean)
  return (
    <div className="text-sm">
      {parts.length > 0 && <p>{parts.join(' · ')}</p>}
      {ticket.location.address && <p className="text-muted">{ticket.location.address.addressLine}, {localize(ticket.location.address.zoneName)}</p>}
    </div>
  )
}

/** The passages offered by the server for this person (H5, H6, H7); empty for a mere reader (H8). */
function Actions({ ticket }: { ticket: Ticket }) {
  const { t } = useTranslation()
  const transitions = useQuery({
    queryKey: ticketKeys.transitions(ticket.id),
    queryFn: () => unwrap(api.GET('/v1/tickets/{ticketId}/transitions', { params: { path: { ticketId: ticket.id } } })),
  })
  const [open, setOpen] = useState<AvailableTransition | null>(null)
  if (!transitions.data || transitions.data.length === 0) return null
  return (
    <Card title={t('tickets.actions')}>
      <div className="flex flex-wrap gap-2">
        {transitions.data.map((transition) => (
          <Button key={transition.code} tone={transition.to.category === 'CANCELLED' ? 'danger' : 'primary'} onClick={() => setOpen(transition)}>
            {t(`transitions.${transition.code}`, { defaultValue: transition.code })}
          </Button>
        ))}
      </div>
      {open && <TransitionForm ticket={ticket} transition={open} onDone={() => setOpen(null)} />}
    </Card>
  )
}

/** The reason and comment a passage needs, then the passage itself (checked again by the server). */
function TransitionForm({ ticket, transition, onDone }: { ticket: Ticket; transition: AvailableTransition; onDone: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [comment, setComment] = useState('')
  const missing = missingInput(transition, reason, comment)
  const fire = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/tickets/{ticketId}/transitions', {
        params: { path: { ticketId: ticket.id } },
        body: {
          transition: transition.code,
          version: ticket.version,
          ...(reason ? { reason: reason as NonNullable<AvailableTransition['reasons']>[number] } : {}),
          ...(comment.trim() ? { comment: comment.trim() } : {}),
        },
      })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ticketKeys.all })
      onDone()
    },
  })
  return (
    <div className="grid gap-3 border-t border-line pt-3">
      {transition.reasonRequired && (
        <SelectField id="transition-reason" label={t('tickets.reason')} value={reason} onChange={(e) => setReason(e.target.value)}
          options={[{ value: '', label: '—' }, ...(transition.reasons ?? []).map((r) => ({ value: r, label: t(`holdReason.${r}`) }))]} />
      )}
      <TextArea id="transition-comment" label={t('tickets.comment')} maxLength={500} rows={2} value={comment} onChange={(e) => setComment(e.target.value)} />
      {missing && <p className="text-sm text-muted">{t(`tickets.missing.${missing}`)}</p>}
      <ErrorMessage message={fire.error ? problemMessage(fire.error, t) : null} />
      <div className="flex gap-2">
        <Button tone="primary" busy={fire.isPending} disabled={missing !== null} onClick={() => fire.mutate()}>{t('tickets.confirm')}</Button>
        <Button onClick={onDone}>{t('tickets.cancelForm')}</Button>
      </div>
    </div>
  )
}

/** H10: one more photo while the ticket is open. */
function AddPhoto({ ticketId }: { ticketId: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const add = useMutation({
    mutationFn: async (file: File) =>
      unwrap(api.POST('/v1/tickets/{ticketId}/photos', { params: { path: { ticketId } }, body: { fileId: await uploadTicketPhoto(file) } })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ticketKeys.one(ticketId) }),
  })
  return (
    <div className="grid gap-1">
      <label className="text-sm font-medium">
        {t('tickets.addPhoto')}
        <input type="file" accept="image/*" capture="environment" disabled={add.isPending}
          onChange={(e) => e.target.files?.[0] && add.mutate(e.target.files[0])} />
      </label>
      <ErrorMessage message={add.error ? problemMessage(add.error, t) : null} />
    </div>
  )
}

/** H9: every step and change, oldest first; people by their part in the minimized view. */
function History({ ticketId }: { ticketId: string }) {
  const { t, i18n } = useTranslation()
  const { localize } = useLocalizedText()
  const history = useQuery({
    queryKey: ticketKeys.history(ticketId),
    queryFn: () => unwrap(api.GET('/v1/tickets/{ticketId}/history', { params: { path: { ticketId } } })),
  })
  if (!history.data) return null
  const format = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'short', timeStyle: 'short' })
  return (
    <Card title={t('tickets.history')}>
      <ol className="grid gap-2 text-sm">
        {history.data.map((entry, index) => (
          <li key={index} className="grid gap-0.5 border-s-2 border-line ps-3">
            <span className="text-muted">{format.format(new Date(entry.at))} · {entry.actor.person
              ? `${entry.actor.person.firstName} ${entry.actor.person.lastName}` : t(`part.${entry.actor.part}`)}</span>
            <span>
              {entry.kind === 'STEP'
                ? entry.transition ? t('tickets.stepTo', { step: entry.to ? localize(entry.to.label) : '' }) : t('tickets.created')
                : t(`activity.${entry.activity}`)}
              {entry.reason ? ` · ${t(`holdReason.${entry.reason}`)}` : ''}
            </span>
            {entry.comment && <span className="whitespace-pre-line text-muted">{entry.comment}</span>}
          </li>
        ))}
      </ol>
    </Card>
  )
}
