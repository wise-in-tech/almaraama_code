import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { Badge, Button, Card, EmptyState, ErrorMessage, Loading, Notice, PageHeader, TextArea, TextField } from '@/shared/ui'
import { useMyMemberships } from '../api/queries'
import { forgetRegistrationCode, rememberedRegistrationCode } from '../lib/registration-code'

type JoinForm = { code: string; comment: string; preRegistrationKey: string }

/**
 * The signed-in person's accesses to residences, pending ones included, with their units (D2, D6, E1); and the form
 * to join one more residence with its QR code, a comment and an optional pre-registration key. Until approval, a
 * pending access shows nothing of the residence (D2 step 4).
 */
export function MyAccessesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const memberships = useMyMemberships()
  const [notice, setNotice] = useState<string | null>(null)
  const { register, handleSubmit, reset, formState } = useForm<JoinForm>({
    defaultValues: { code: rememberedRegistrationCode(), comment: '', preRegistrationKey: '' },
  })
  const join = useMutation({
    mutationFn: (form: JoinForm) =>
      unwrap(
        api.POST('/v1/me/access-requests', {
          body: {
            code: form.code.trim(),
            ...(form.comment.trim() ? { comment: form.comment.trim() } : {}),
            ...(form.preRegistrationKey.trim() ? { preRegistrationKey: form.preRegistrationKey.trim() } : {}),
          },
        }),
      ),
    onSuccess: (membership) => {
      forgetRegistrationCode()
      reset({ code: '', comment: '', preRegistrationKey: '' })
      setNotice(membership.status === 'ACTIVE' ? t('access.joinedActive') : t('access.joinedPending'))
      void queryClient.invalidateQueries({ queryKey: ['me'] })
    },
  })

  return (
    <section className="grid gap-4">
      <PageHeader title={t('access.title')} intro={t('access.intro')} />
      {memberships.isPending && <Loading />}
      {memberships.isError && <ErrorMessage message={problemMessage(memberships.error, t)} />}
      {memberships.data && memberships.data.length === 0 && <EmptyState message={t('access.none')} />}
      {memberships.data?.map((membership) => (
        <Card key={membership.membershipId}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="font-semibold">{membership.siteName}</p>
              <p className="text-sm text-muted">{membership.organizationName}</p>
            </div>
            <Badge tone={membership.status === 'ACTIVE' ? 'good' : 'warn'}>
              {t(`membershipStatus.${membership.status}`)}
            </Badge>
          </div>
          {membership.occupancies.length > 0 && (
            <ul className="grid gap-1 text-sm">
              {membership.occupancies.map((occupancy) => (
                <li key={occupancy.occupancyId}>
                  {`${t(`unitType.${occupancy.unitType}`)} ${occupancy.unitReference} · ${t(`occupancyRole.${occupancy.role}`)}`}
                </li>
              ))}
            </ul>
          )}
        </Card>
      ))}
      <Card title={t('access.joinTitle')}>
        <form noValidate onSubmit={handleSubmit((form) => join.mutate(form))} className="grid gap-3">
          <TextField id="join-code" label={t('registration.code')} dir="ltr" autoCapitalize="characters"
            error={formState.errors.code?.message}
            {...register('code', { required: t('validation.required') })} />
          <TextArea id="join-comment" label={t('registration.comment')} hint={t('registration.commentHint')}
            maxLength={500} {...register('comment')} />
          <TextField id="join-key" label={t('registration.key')} hint={t('registration.keyHint')} dir="ltr"
            autoComplete="off" {...register('preRegistrationKey')} />
          <ErrorMessage message={join.error ? problemMessage(join.error, t) : null} />
          <Notice message={notice} />
          <Button type="submit" tone="primary" busy={join.isPending}>
            {t('access.join')}
          </Button>
        </form>
      </Card>
    </section>
  )
}
