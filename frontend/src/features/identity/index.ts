import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { ForgotPasswordPage } from './routes/ForgotPasswordPage'
import { InvitationPage } from './routes/InvitationPage'
import { LoginPage } from './routes/LoginPage'
import { ResetPasswordPage } from './routes/ResetPasswordPage'
import { VerifyEmailPage } from './routes/VerifyEmailPage'

export { useMe } from './api/me'

/**
 * Public routes of the identity feature (ADR-030): sign-in, forgotten password, and the three kinds of account link
 * (set my password, verify my e-mail, reset my password). The shell mounts them.
 */
export function identityRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({
      getParentRoute: () => parent,
      path: 'login',
      component: LoginPage,
      validateSearch: (search: Record<string, unknown>): { redirect?: string } =>
        typeof search.redirect === 'string' ? { redirect: search.redirect } : {},
    }),
    createRoute({ getParentRoute: () => parent, path: 'invitation', component: InvitationPage }),
    createRoute({ getParentRoute: () => parent, path: 'forgot-password', component: ForgotPasswordPage }),
    createRoute({ getParentRoute: () => parent, path: 'reset-password', component: ResetPasswordPage }),
    createRoute({ getParentRoute: () => parent, path: 'verify-email', component: VerifyEmailPage }),
  ] as const
}
