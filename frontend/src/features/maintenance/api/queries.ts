import { useQuery } from '@tanstack/react-query'
import { api, fileWithFields, unwrap, type components } from '@/shared/api'
import { shrinkPhoto } from '@/shared/photo'

export type TicketSummary = components['schemas']['TicketSummary']
export type Ticket = components['schemas']['Ticket']
export type AvailableTransition = components['schemas']['AvailableTransition']
export type NodeCategory = components['schemas']['NodeCategory']
export type HoldReason = components['schemas']['HoldReason']
export type IssueCatalogCategory = components['schemas']['IssueCatalogCategory']
export type IssueTypeConfig = components['schemas']['IssueTypeConfig']
export type TicketView = 'ALL' | 'MINE' | 'COMMON'

/** Query keys of the feature, so that a change refreshes exactly what it touched. */
export const ticketKeys = {
  all: ['tickets'] as const,
  list: (view: TicketView, siteId?: string, categories?: NodeCategory[]) => ['tickets', 'list', view, siteId ?? '', categories ?? []] as const,
  one: (ticketId: string) => ['tickets', ticketId] as const,
  transitions: (ticketId: string) => ['tickets', ticketId, 'transitions'] as const,
  history: (ticketId: string) => ['tickets', ticketId, 'history'] as const,
}

/** H8, M3: the tickets the person may see; the server applies the relations and permissions (ADR-037). */
export function useTickets(view: TicketView, siteId?: string, categories?: NodeCategory[]) {
  return useQuery({
    queryKey: ticketKeys.list(view, siteId, categories),
    queryFn: () =>
      unwrap(api.GET('/v1/tickets', {
        params: { query: { view, size: 50, ...(siteId ? { siteId } : {}), ...(categories?.length ? { category: categories } : {}) } },
      })),
  })
}

/** One ticket; {@code minimized} for a common ticket seen as a mere member (ADR-037). */
export function useTicket(ticketId: string) {
  return useQuery({
    queryKey: ticketKeys.one(ticketId),
    queryFn: () => unwrap(api.GET('/v1/tickets/{ticketId}', { params: { path: { ticketId } } })),
  })
}

/** H2, criterion 8: what may be reported on the site, filtered by the family of an equipment. */
export function useIssueCatalog(siteId: string, assetId?: string) {
  return useQuery({
    queryKey: ['issue-catalog', siteId, assetId ?? ''],
    queryFn: () =>
      unwrap(api.GET('/v1/sites/{siteId}/issue-catalog', { params: { path: { siteId }, query: assetId ? { assetId } : {} } })),
  })
}

/**
 * H10, ADR-038: shrinks a photo on the phone, then uploads it for a ticket; the server checks and re-encodes it. Returns
 * the identifier to attach.
 */
export async function uploadTicketPhoto(file: File): Promise<string> {
  const photo = await shrinkPhoto(file)
  const uploaded = await unwrap(api.POST('/v1/files', fileWithFields(photo, { purpose: 'TICKET_PHOTO' as const })))
  return uploaded.id
}
