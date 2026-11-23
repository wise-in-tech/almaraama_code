import { useQueryClient } from '@tanstack/react-query'
import { Link, Outlet, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { SupportBanner } from '@/features/membership'
import { NotificationBell } from '@/features/notification'
import { endSession, getAccessToken, SESSION_ENDED_EVENT } from '@/shared/api'
import { LanguageSwitch } from './LanguageSwitch'

/**
 * Frame shared by every role: header with the notification bell (K1), the support banner of a platform administrator
 * (ADR-035), and the current page. Role modules are loaded on demand (ADR-009).
 */
export function AppShell() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const signedIn = getAccessToken() !== null

  useEffect(() => {
    const onSessionEnded = () => {
      queryClient.clear()
      void navigate({ to: '/login' })
    }
    window.addEventListener(SESSION_ENDED_EVENT, onSessionEnded)
    return () => window.removeEventListener(SESSION_ENDED_EVENT, onSessionEnded)
  }, [navigate, queryClient])

  const logout = async () => {
    await endSession()
    queryClient.clear()
    await navigate({ to: '/login' })
  }

  return (
    <div className="min-h-dvh">
      {signedIn && <SupportBanner />}
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-surface px-4 py-3">
        <Link to="/" className="block">
          <p className="text-lg font-semibold text-brand">{t('app.name')}</p>
          <p className="text-sm text-muted">{t('app.tagline')}</p>
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <LanguageSwitch />
          {signedIn && <NotificationBell />}
          {signedIn && (
            <button type="button" onClick={() => void logout()} className="rounded-md border border-line px-3 py-1 text-sm">
              {t('session.logout')}
            </button>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
