import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router'
import { customizationRoutes } from '@/features/customization'
import { identityRoutes } from '@/features/identity'
import { membershipRoutes, publicMembershipRoutes } from '@/features/membership'
import { sitesRoutes } from '@/features/sites'
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
  beforeLoad: async () => {
    if (getAccessToken() === null && !(await refreshSession())) {
      throw redirect({ to: '/login' })
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
  ]),
])

/** The application router; routes are preloaded when a link is hovered or focused. */
export const router = createRouter({ routeTree, defaultPreload: 'intent' })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
