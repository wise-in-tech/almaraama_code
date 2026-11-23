import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { api, isProblem, problemMessage, startSession } from '@/shared/api'
import { log } from '@/shared/log'
import { Field, FormMessage, PasswordField, SubmitButton } from '../components/fields'
import { emailSchema } from '../lib/account-links'

const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'validation.password'),
})
type LoginForm = z.infer<typeof loginSchema>

/**
 * Sign-in with e-mail and password (D5, ADR-030). One main button, as the spec asks. When the password is right but
 * the e-mail is not verified yet, the page offers to send the verification link again.
 */
export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [failure, setFailure] = useState<string | null>(null)
  const [unverified, setUnverified] = useState(false)
  const { register, handleSubmit, formState } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFailure(null)
    setUnverified(false)
    const { data, error } = await api.POST('/v1/auth/login', { body: { email, password } })
    if (error || !data) {
      setUnverified(isProblem(error) && error.code === 'EMAIL_NOT_VERIFIED')
      setFailure(problemMessage(error, t))
      log.debug('identity', 'sign-in refused', { code: isProblem(error) ? error.code : 'unknown' })
      return
    }
    startSession(data)
    await navigate({ to: '/' })
  })

  return (
    <section className="mx-auto grid max-w-sm gap-5">
      <h1 className="text-2xl font-semibold">{t('login.title')}</h1>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <Field
          id="login-email"
          label={t('login.email')}
          type="email"
          inputMode="email"
          autoComplete="username"
          error={formState.errors.email?.message && t(formState.errors.email.message)}
          {...register('email')}
        />
        <PasswordField
          id="login-password"
          label={t('login.password')}
          autoComplete="current-password"
          error={formState.errors.password?.message && t(formState.errors.password.message)}
          {...register('password')}
        />
        <FormMessage message={failure} />
        {unverified && (
          <Link to="/verify-email" className="text-sm text-brand underline">
            {t('login.resendVerification')}
          </Link>
        )}
        <SubmitButton label={t('login.submit')} busy={formState.isSubmitting} />
      </form>
      <Link to="/forgot-password" className="text-sm text-brand underline">
        {t('login.forgot')}
      </Link>
    </section>
  )
}
