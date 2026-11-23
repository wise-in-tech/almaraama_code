import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, startSession } from '@/shared/api'
import { FormMessage, PasswordField, SubmitButton } from '../components/fields'
import { newPasswordSchema, takeTokenFromUrl, type NewPasswordForm } from '../lib/account-links'

/**
 * The "set my password" link of an account created by a manager (D4, ADR-030): greet the person, then let them
 * choose their password, which also verifies their e-mail, and sign them in.
 */
export function InvitationPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [token] = useState(takeTokenFromUrl)
  const [failure, setFailure] = useState<string | null>(null)
  const { register, handleSubmit, formState } = useForm<NewPasswordForm>({ resolver: zodResolver(newPasswordSchema) })

  const preview = useQuery({
    queryKey: ['invitation', token],
    enabled: token.length > 0,
    retry: false,
    staleTime: Infinity,
    queryFn: async () => {
      const { data, error } = await api.POST('/v1/invitations/inspect', { body: { token } })
      if (error) throw error
      return data
    },
  })

  const invitedLanguage = preview.data?.language
  useEffect(() => {
    if (invitedLanguage) void i18n.changeLanguage(invitedLanguage.toLowerCase())
  }, [invitedLanguage, i18n])

  const onSubmit = handleSubmit(async ({ password }) => {
    setFailure(null)
    const { data, error } = await api.POST('/v1/invitations/accept', { body: { token, password } })
    if (error || !data) {
      setFailure(problemMessage(error, t))
      return
    }
    startSession(data)
    await navigate({ to: '/' })
  })

  if (!token) return <FormMessage message={t('links.missing')} />
  if (preview.isPending) return <p className="text-muted">{t('links.checking')}</p>
  if (preview.isError) return <FormMessage message={t('errors.codes.LINK_INVALID_OR_EXPIRED')} />

  return (
    <section className="mx-auto grid max-w-sm gap-5">
      <h1 className="text-2xl font-semibold">{t('invitation.greeting', { name: preview.data.firstName })}</h1>
      <p className="text-muted">{t('invitation.intro', { email: preview.data.email })}</p>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <PasswordField
          id="invitation-password"
          label={t('password.new')}
          autoComplete="new-password"
          error={formState.errors.password?.message && t(formState.errors.password.message)}
          {...register('password')}
        />
        <PasswordField
          id="invitation-confirm"
          label={t('password.confirm')}
          autoComplete="new-password"
          error={formState.errors.confirm?.message && t(formState.errors.confirm.message)}
          {...register('confirm')}
        />
        <p className="text-sm text-muted">{t('password.rules')}</p>
        <FormMessage message={failure} />
        <SubmitButton label={t('invitation.submit')} busy={formState.isSubmitting} />
      </form>
    </section>
  )
}
