import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router'
import { assetsRoutes } from '@/features/assets'
import { customizationRoutes } from '@/features/customization'
import { identityRoutes } from '@/features/identity'
import { maintenanceRoutes } from '@/features/maintenance'
import { membershipRoutes, publicMembershipRoutes } from '@/features/membership'
import { sitesRoutes } from '@/features/sites'
import { workflowRoutes } from '@/features/workflow'
import { getAccessToken, refreshSession } from '@/shared/api'
import { AppShell } from './shell/AppShell'
import { HomePage } from './shell/HomePage'
import { NotFoundPage } from './shell/NotFoundPage'

const rootRoute = createRootRoute({ component: AppShell, notFoundComponent: NotFoundPage })

/**
 * Everything below needs a session. Without an access token in memory (first load, reload), the refresh cookie
 * is tried once; without a valid session the person goes to sign-in. The server checks every request anyway.
 */
const signedInRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'signed-in',
  beforeLoad: async ({ location }) => {
    if (getAccessToken() === null && !(await refreshSession())) {
      // Back to the page asked for after sign-in (a scanned label, criterion 8); checked by the sign-in page.
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
})

const homeRoute = createRoute({ getParentRoute: () => signedInRoute, path: '/', component: HomePage })

/*
 * Each feature exposes its routes through its public index.ts, and the shell assembles them here.
 * Routes are code-based on purpose: a feature never imports the shell, the shell imports features.
 */
const routeTree = rootRoute.addChildren([
  ...identityRoutes(rootRoute),
  ...publicMembershipRoutes(rootRoute),
  signedInRoute.addChildren([
    homeRoute,
    ...membershipRoutes(signedInRoute),
    ...sitesRoutes(signedInRoute),
    ...customizationRoutes(signedInRoute),
    ...assetsRoutes(signedInRoute),
    ...maintenanceRoutes(signedInRoute),
    ...workflowRoutes(signedInRoute),
  ]),
])

/** The application router; routes are preloaded when a link is hovered or focused. */
export const router = createRouter({ routeTree, defaultPreload: 'intent' })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
