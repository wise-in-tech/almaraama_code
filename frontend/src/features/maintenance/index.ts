import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { IssueTypeConfigsPage } from './routes/IssueTypeConfigsPage'
import { ReportTicketPage } from './routes/ReportTicketPage'
import { TicketPage } from './routes/TicketPage'
import { TicketsPage } from './routes/TicketsPage'

/**
 * Signed-in routes of the maintenance feature (H1 to H10, criteria 8 to 10): my tickets and the common tickets, the
 * report in three screens (also opened by the QR code of an equipment), one ticket with its passages and history, and
 * the routing of the issue types of a residence. The server decides what each person sees and may do.
 */
export function maintenanceRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({ getParentRoute: () => parent, path: 'tickets', component: TicketsPage }),
    createRoute({ getParentRoute: () => parent, path: 'tickets/$ticketId', component: TicketPage }),
    createRoute({
      getParentRoute: () => parent,
      path: 'sites/$siteId/report',
      component: ReportTicketPage,
      validateSearch: (search: Record<string, unknown>): { assetId?: string } =>
        typeof search.assetId === 'string' ? { assetId: search.assetId } : {},
    }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/issue-types', component: IssueTypeConfigsPage }),
  ] as const
}
