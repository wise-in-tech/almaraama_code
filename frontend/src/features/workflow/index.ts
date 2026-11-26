import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { OrganizationWorkflowPage, WorkflowsAdminPage } from './routes/WorkflowsAdminPage'

/**
 * Signed-in routes of the workflow feature (H5, C12, ADR-036): the back-office of the ticket circuits and of the
 * circuit and options of each client. Platform permissions only; the server checks every request.
 */
export function workflowRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({ getParentRoute: () => parent, path: 'admin/workflows', component: WorkflowsAdminPage }),
    createRoute({ getParentRoute: () => parent, path: 'admin/organizations/$organizationId/workflow', component: OrganizationWorkflowPage }),
  ] as const
}
