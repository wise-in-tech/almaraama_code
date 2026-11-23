import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { UnitCategoriesAdminPage, ZonesAdminPage } from './routes/admin/ReferenceAdminPages'
import { SitePage } from './routes/SitePage'
import { SitesPage } from './routes/SitesPage'
import { UnitCategoriesPage } from './routes/UnitCategoriesPage'
import { UnitsPage } from './routes/UnitsPage'

/**
 * Signed-in routes of the sites feature (A1 to A9, criteria 1 to 3): residences and their creation, one residence
 * with its buildings, its units with the Excel import, the unit categories of an organization, and the back-office of
 * the zones and default categories.
 */
export function sitesRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({ getParentRoute: () => parent, path: 'organizations/$organizationId/sites', component: SitesPage }),
    createRoute({ getParentRoute: () => parent, path: 'organizations/$organizationId/unit-categories', component: UnitCategoriesPage }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId', component: SitePage }),
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/units', component: UnitsPage }),
    createRoute({ getParentRoute: () => parent, path: 'admin/zones', component: ZonesAdminPage }),
    createRoute({ getParentRoute: () => parent, path: 'admin/unit-categories', component: UnitCategoriesAdminPage }),
  ] as const
}
