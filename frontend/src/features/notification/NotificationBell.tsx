import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, unwrap } from '@/shared/api'
import { Button, EmptyState } from '@/shared/ui'

const REFRESH_MS = 60_000

/**
 * The notification bell (K1, K6: in the application only): the number of unread notifications, refreshed every
 * minute, and the latest ones in the reader's language, written from their template and values. Opening one marks it
 * read and goes to its page.
 */
export function NotificationBell() {
  const { t } = useTranslation()
  const router = useRouter()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    refetchInterval: REFRESH_MS,
    queryFn: () => unwrap(api.GET('/v1/notifications/unread-count')),
  })
  const latest = useQuery({
    queryKey: ['notifications', 'latest'],
    enabled: open,
    queryFn: () => unwrap(api.GET('/v1/notifications', { params: { query: { size: 10 } } })),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const read = useMutation({
    mutationFn: (notificationId: string) =>
      unwrap(api.POST('/v1/notifications/{notificationId}/read', { params: { path: { notificationId } } })),
    onSuccess: refresh,
  })
  const readAll = useMutation({ mutationFn: () => unwrap(api.POST('/v1/notifications/read-all')), onSuccess: refresh })
  const count = unread.data?.count ?? 0

  return (
    <div className="relative">
      <Button aria-expanded={open} aria-label={t('notifications.bell', { count })} onClick={() => setOpen((shown) => !shown)}>
        <span aria-hidden="true">🔔</span>
        {count > 0 && <span className="ms-1 rounded-full bg-brand px-2 text-surface">{count}</span>}
      </Button>
      {open && (
        <div className="absolute end-0 z-10 mt-2 grid w-80 max-w-[90vw] gap-2 rounded-xl border border-line bg-surface p-3 shadow-lg">
          {latest.data?.items.length === 0 && <EmptyState message={t('notifications.none')} />}
          <ul className="grid gap-1">
            {latest.data?.items.map((notification) => (
              <li key={notification.id}>
                <button
                  type="button"
                  className={`w-full rounded-lg p-2 text-start text-sm hover:bg-ground ${notification.read ? 'text-muted' : 'font-semibold'}`}
                  onClick={() => {
                    read.mutate(notification.id)
                    setOpen(false)
                    if (notification.link) router.history.push(notification.link)
                  }}
                >
                  {t(`notifications.templates.${notification.template}`, notification.params)}
                </button>
              </li>
            ))}
          </ul>
          {count > 0 && <Button onClick={() => readAll.mutate()}>{t('notifications.readAll')}</Button>}
        </div>
      )}
    </div>
  )
}
