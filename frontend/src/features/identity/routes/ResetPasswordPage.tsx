import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, problemMessage } from '@/shared/api'
import { FormMessage, FormNotice, PasswordField, SubmitButton } from '../components/fields'
import { newPasswordSchema, takeTokenFromUrl, type NewPasswordForm } from '../lib/account-links'

/**
 * The reset link of a forgotten password (D5, ADR-030): choose a new password. Every session of the person ends; they
 * then sign in with the new password.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const [token] = useState(takeTokenFromUrl)
  const [failure, setFailure] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const { register, handleSubmit, formState } = useForm<NewPasswordForm>({ resolver: zodResolver(newPasswordSchema) })

  const onSubmit = handleSubmit(async ({ password }) => {
    setFailure(null)
    const { error } = await api.POST('/v1/auth/password-reset/confirm', { body: { token, password } })
    if (error) {
      setFailure(problemMessage(error, t))
      return
    }
    setDone(true)
  })

  if (!token) return <FormMessage message={t('links.missing')} />

  return (
    <section className="mx-auto grid max-w-sm gap-5">
      <h1 className="text-2xl font-semibold">{t('reset.title')}</h1>
      {done ? (
        <FormNotice message={t('reset.done')} />
      ) : (
        <form noValidate onSubmit={onSubmit} className="grid gap-4">
          <PasswordField
            id="reset-password"
            label={t('password.new')}
            autoComplete="new-password"
            error={formState.errors.password?.message && t(formState.errors.password.message)}
            {...register('password')}
          />
          <PasswordField
            id="reset-confirm"
            label={t('password.confirm')}
            autoComplete="new-password"
            error={formState.errors.confirm?.message && t(formState.errors.confirm.message)}
            {...register('confirm')}
          />
          <p className="text-sm text-muted">{t('password.rules')}</p>
          <FormMessage message={failure} />
          <SubmitButton label={t('reset.submit')} busy={formState.isSubmitting} />
        </form>
      )}
      <Link to="/login" className="text-sm text-brand underline">
        {t('links.backToLogin')}
      </Link>
    </section>
  )
}
