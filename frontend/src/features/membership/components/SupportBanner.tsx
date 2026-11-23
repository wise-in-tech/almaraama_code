import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, unwrap } from '@/shared/api'
import { Button } from '@/shared/ui'
import { useMyAccess } from '../api/queries'

/**
 * Permanent banner while a platform administrator is in a support session (C12, ADR-035): the organization, the end
 * time, and a button to end it now. Shown by the shell on every page.
 */
export function SupportBanner() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const access = useMyAccess()
  const session = access.data?.supportSession
  const end = useMutation({
    mutationFn: (supportSessionId: string) =>
      unwrap(api.POST('/v1/admin/support-sessions/{supportSessionId}/end', { params: { path: { supportSessionId } } })),
    onSuccess: () => queryClient.invalidateQueries(),
  })
  if (!session) return null
  const until = new Date(session.expiresAt).toLocaleTimeString(i18n.language, { hour: '2-digit', minute: '2-digit' })
  return (
    <div role="status" className="flex flex-wrap items-center justify-between gap-2 bg-amber-100 px-4 py-2 text-sm text-amber-900">
      <span>{t('support.banner', { organization: session.organization.name, until })}</span>
      <Button busy={end.isPending} onClick={() => end.mutate(session.id)}>{t('support.end')}</Button>
    </div>
  )
}
