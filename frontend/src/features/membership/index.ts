import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { AccessRequestsPage } from './routes/AccessRequestsPage'
import { OrganizationsAdminPage, SupportSessionsPage } from './routes/admin/OrganizationsAdminPage'
import { MyAccessesPage } from './routes/MyAccessesPage'
import { PeopleAndRolesPage } from './routes/PeopleAndRolesPage'
import { PreRegistrationsPage } from './routes/PreRegistrationsPage'
import { RegistrationPage } from './routes/RegistrationPage'
import { RegistrationQrPage } from './routes/RegistrationQrPage'

export { canOnPlatform, canOnSite, useMyAccess, useMyMemberships, type MyAccess } from './api/queries'
export { SupportBanner } from './components/SupportBanner'

/** Public routes of the membership feature: the registration form of a QR code (D2, ADR-034). */
export function publicMembershipRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [createRoute({ getParentRoute: () => parent, path: 'r', component: RegistrationPage })] as const
}

/**
 * Signed-in routes of the membership feature (D2, D3, C9, C12): my accesses, the registration QR code, access
 * requests, pre-registrations and resident import of a site, people and roles of an organization, and the back-office
 * of organizations and support sessions. Menus come from {@code /v1/me/access}; the server checks every request.
 */
export function membershipRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({ getParentRoute: () => parent, path: 'me/accesses', component: MyAccessesPage }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/registration-qr', component: RegistrationQrPage }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/memberships', component: AccessRequestsPage }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/pre-registrations', component: PreRegistrationsPage }),
    createRoute({ getParentRoute: () => parent, path: 'organizations/$organizationId/people', component: PeopleAndRolesPage }),
    createRoute({ getParentRoute: () => parent, path: 'admin/organizations', component: OrganizationsAdminPage }),
    createRoute({ getParentRoute: () => parent, path: 'admin/support', component: SupportSessionsPage }),
  ] as const
}
