import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { useCallback, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { AltchaField } from '@/shared/altcha'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { DEFAULT_PHONE_COUNTRY, PhoneField, toE164 } from '@/shared/phone'
import { Button, ErrorMessage, Notice, SelectField, TextArea, TextField } from '@/shared/ui'
import { forgetRegistrationCode, takeRegistrationCode } from '../lib/registration-code'

type Language = components['schemas']['Language']

type RegistrationForm = {
  code: string
  firstName: string
  lastName: string
  email: string
  phoneCountry: string
  phone: string
  language: Language
  comment: string
  password: string
  preRegistrationKey: string
}

/**
 * Registration by the QR code of a residence (D2, D3, criterion 5, ADR-034), public. Shows the residence's name, then
 * the form: names, e-mail, phone, language, a free comment ("Apartment B12, owner"), a password and, optionally, the
 * personal key of a pre-registration. Protected by ALTCHA. The answer is always the same ("check your e-mail"),
 * whether the e-mail already had an account or the key was right: the request reaches the manager once the e-mail is
 * confirmed. A person who already has an account signs in and uses "my accesses".
 */
export function RegistrationPage() {
  const { t, i18n } = useTranslation()
  const [initialCode] = useState(takeRegistrationCode)
  const [proof, setProof] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const onPayload = useCallback((payload: string | null) => setProof(payload), [])
  const { register, handleSubmit, control, formState, setError } = useForm<RegistrationForm>({
    defaultValues: {
      code: initialCode,
      firstName: '',
      lastName: '',
      email: '',
      phoneCountry: DEFAULT_PHONE_COUNTRY,
      phone: '',
      language: (i18n.language.toUpperCase() as Language) ?? 'AR',
      comment: '',
      password: '',
      preRegistrationKey: '',
    },
  })
  const code = useWatch({ control, name: 'code' }).trim()
  const preview = useQuery({
    queryKey: ['registration-qr', 'preview', code],
    enabled: code.replaceAll('-', '').length >= 8,
    retry: false,
    queryFn: () => unwrap(api.POST('/v1/registration-qr/inspect', { body: { code } })),
  })

  const onSubmit = handleSubmit(async (form) => {
    if (!proof) return
    setFailure(null)
    const phone = toE164(form.phoneCountry, form.phone)
    if (!phone) {
      setError('phone', { message: t('validation.phoneInvalid') })
      return
    }
    const { error } = await api.POST('/v1/registrations', {
      params: { header: { 'X-Altcha': proof } },
      body: {
        code: form.code.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone,
        language: form.language,
        password: form.password,
        ...(form.comment.trim() ? { comment: form.comment.trim() } : {}),
        ...(form.preRegistrationKey.trim() ? { preRegistrationKey: form.preRegistrationKey.trim() } : {}),
      },
    })
    if (error) {
      setFailure(problemMessage(error, t))
      return
    }
    forgetRegistrationCode()
    setDone(true)
  })

  if (done) {
    return (
      <section className="mx-auto grid max-w-md gap-4">
        <h1 className="text-2xl font-semibold">{t('registration.doneTitle')}</h1>
        <Notice message={t('registration.done')} />
      </section>
    )
  }

  const required = { required: t('validation.required') }
  return (
    <section className="mx-auto grid max-w-md gap-5">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold">{t('registration.title')}</h1>
        {preview.data ? (
          <p className="text-lg">{t('registration.site', preview.data)}</p>
        ) : (
          <p className="text-muted">{t('registration.intro')}</p>
        )}
      </header>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <TextField
          id="registration-code"
          label={t('registration.code')}
          dir="ltr"
          autoCapitalize="characters"
          error={preview.isError ? t('errors.codes.REGISTRATION_CODE_INVALID') : formState.errors.code?.message}
          {...register('code', required)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField id="registration-first" label={t('person.firstName')} autoComplete="given-name"
            error={formState.errors.firstName?.message} {...register('firstName', required)} />
          <TextField id="registration-last" label={t('person.lastName')} autoComplete="family-name"
            error={formState.errors.lastName?.message} {...register('lastName', required)} />
        </div>
        <TextField id="registration-email" label={t('login.email')} type="email" dir="ltr" autoComplete="email"
          error={formState.errors.email?.message} {...register('email', required)} />
        <PhoneField
          id="registration-phone"
          label={t('person.phone')}
          countryLabel={t('login.phoneCountry')}
          error={formState.errors.phone?.message}
          country={register('phoneCountry')}
          number={register('phone', required)}
        />
        <SelectField
          id="registration-language"
          label={t('person.language')}
          options={(['AR', 'EN', 'FR'] as const).map((value) => ({ value, label: t(`language.${value.toLowerCase()}`) }))}
          {...register('language')}
        />
        <TextArea id="registration-comment" label={t('registration.comment')} hint={t('registration.commentHint')}
          maxLength={500} {...register('comment')} />
        <TextField id="registration-password" label={t('password.new')} type="password" dir="ltr"
          autoComplete="new-password" hint={t('password.rules')} error={formState.errors.password?.message}
          {...register('password', { ...required, minLength: { value: 8, message: t('validation.passwordTooShort') } })} />
        <TextField id="registration-key" label={t('registration.key')} hint={t('registration.keyHint')} dir="ltr"
          autoComplete="off" autoCapitalize="characters" {...register('preRegistrationKey')} />
        <AltchaField onPayload={onPayload} />
        <ErrorMessage message={failure} />
        <Button type="submit" tone="primary" busy={formState.isSubmitting} disabled={!proof}>
          {t('registration.submit')}
        </Button>
      </form>
      <p className="text-sm">
        {t('registration.haveAccount')}{' '}
        <Link to="/me/accesses" className="text-brand underline">
          {t('registration.signInAndJoin')}
        </Link>
      </p>
    </section>
  )
}
