import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from '@tanstack/react-router'
import { useCallback, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { AltchaField } from '@/shared/altcha'
import { api, problemMessage } from '@/shared/api'
import { Field, FormMessage, FormNotice, SubmitButton } from '../components/fields'
import { emailSchema } from '../lib/account-links'

const forgotSchema = z.object({ email: emailSchema })
type ForgotForm = z.infer<typeof forgotSchema>

/**
 * Forgotten password (D5, ADR-030): the person types their e-mail and receives a reset link valid one hour. The
 * answer is the same whether or not an account exists. Protected by ALTCHA (ADR-032): the button waits for the
 * proof, computed in the background.
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [proof, setProof] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const { register, handleSubmit, formState } = useForm<ForgotForm>({
    resolver: zodResolver(forgotSchema),
    defaultValues: { email: '' },
  })
  const onPayload = useCallback((payload: string | null) => setProof(payload), [])

  const onSubmit = handleSubmit(async ({ email }) => {
    if (!proof) return
    setFailure(null)
    const { error } = await api.POST('/v1/auth/password-reset', {
      params: { header: { 'X-Altcha': proof } },
      body: { email },
    })
    if (error) {
      setFailure(problemMessage(error, t))
      return
    }
    setSent(true)
  })

  return (
    <section className="mx-auto grid max-w-sm gap-5">
      <h1 className="text-2xl font-semibold">{t('forgot.title')}</h1>
      {sent ? (
        <FormNotice message={t('forgot.sent')} />
      ) : (
        <form noValidate onSubmit={onSubmit} className="grid gap-4">
          <p className="text-muted">{t('forgot.intro')}</p>
          <Field
            id="forgot-email"
            label={t('login.email')}
            type="email"
            inputMode="email"
            autoComplete="username"
            error={formState.errors.email?.message && t(formState.errors.email.message)}
            {...register('email')}
          />
          <AltchaField onPayload={onPayload} />
          <FormMessage message={failure} />
          <SubmitButton label={t('forgot.submit')} busy={formState.isSubmitting} disabled={!proof} />
        </form>
      )}
      <Link to="/login" className="text-sm text-brand underline">
        {t('links.backToLogin')}
      </Link>
    </section>
  )
}
