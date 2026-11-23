import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { useCallback, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { AltchaField } from '@/shared/altcha'
import { api, problemMessage } from '@/shared/api'
import { Field, FormMessage, FormNotice, SubmitButton } from '../components/fields'
import { emailSchema, takeTokenFromUrl } from '../lib/account-links'

const resendSchema = z.object({ email: emailSchema })
type ResendForm = z.infer<typeof resendSchema>

/**
 * E-mail verification (D1, ADR-030). With a link (`#secret`), the e-mail is verified at once and the person can sign
 * in. Without a link, the page offers to send a new one, protected by ALTCHA (ADR-032).
 */
export function VerifyEmailPage() {
  const { t } = useTranslation()
  const [token] = useState(takeTokenFromUrl)

  const verification = useQuery({
    queryKey: ['verify-email', token],
    enabled: token.length > 0,
    retry: false,
    staleTime: Infinity,
    queryFn: async () => {
      const { error } = await api.POST('/v1/accounts/verify-email', { body: { token } })
      if (error) throw error
      return true
    },
  })

  return (
    <section className="mx-auto grid max-w-sm gap-5">
      <h1 className="text-2xl font-semibold">{t('verify.title')}</h1>
      {token && verification.isPending && <p className="text-muted">{t('links.checking')}</p>}
      {token && verification.isSuccess && <FormNotice message={t('verify.done')} />}
      {token && verification.isError && <FormMessage message={problemMessage(verification.error, t)} />}
      {(!token || verification.isError) && <ResendForm />}
      <Link to="/login" className="text-sm text-brand underline">
        {t('links.backToLogin')}
      </Link>
    </section>
  )
}

/** Asks for a new verification link (always the same answer, D1). */
function ResendForm() {
  const { t } = useTranslation()
  const [proof, setProof] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const { register, handleSubmit, formState } = useForm<ResendForm>({
    resolver: zodResolver(resendSchema),
    defaultValues: { email: '' },
  })
  const onPayload = useCallback((payload: string | null) => setProof(payload), [])

  const onSubmit = handleSubmit(async ({ email }) => {
    if (!proof) return
    setFailure(null)
    const { error } = await api.POST('/v1/accounts/verify-email/resend', {
      params: { header: { 'X-Altcha': proof } },
      body: { email },
    })
    if (error) {
      setFailure(problemMessage(error, t))
      return
    }
    setSent(true)
  })

  if (sent) return <FormNotice message={t('verify.sent')} />
  return (
    <form noValidate onSubmit={onSubmit} className="grid gap-4">
      <p className="text-muted">{t('verify.intro')}</p>
      <Field
        id="verify-email"
        label={t('login.email')}
        type="email"
        inputMode="email"
        autoComplete="username"
        error={formState.errors.email?.message && t(formState.errors.email.message)}
        {...register('email')}
      />
      <AltchaField onPayload={onPayload} />
      <FormMessage message={failure} />
      <SubmitButton label={t('verify.submit')} busy={formState.isSubmitting} disabled={!proof} />
    </form>
  )
}
