import { useState, type InputHTMLAttributes } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

type FieldProps = InputHTMLAttributes<HTMLInputElement> & { id: string; label: string; error?: string | undefined }

/**
 * Labelled input with a large touch target (spec: wide targets, plain words). E-mails and passwords stay
 * left-to-right even in Arabic, so they read the same way in every language.
 */
export function Field({ id, label, error, className, ...input }: FieldProps) {
  return (
    <div className="grid gap-1">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={id}
        dir="ltr"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={cn(
          'min-h-12 rounded-lg border border-line bg-surface px-3 text-lg tracking-wide focus:border-brand focus:outline-none',
          error && 'border-red-700',
          className,
        )}
        {...input}
      />
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * A password input with a button that shows or hides what was typed (ASVS V2.1.12), so people can check a long
 * password on a phone.
 */
export function PasswordField(props: FieldProps) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)
  return (
    <div className="grid gap-1">
      <Field {...props} type={visible ? 'text' : 'password'} />
      <button
        type="button"
        onClick={() => setVisible((shown) => !shown)}
        aria-controls={props.id}
        aria-pressed={visible}
        className="justify-self-start text-sm text-brand underline"
      >
        {visible ? t('password.hide') : t('password.show')}
      </button>
    </div>
  )
}

/** A short confirmation shown after a request that sends an e-mail, announced to screen readers. */
export function FormNotice({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p role="status" className="rounded-lg border border-brand bg-surface p-3 text-sm">
      {message}
    </p>
  )
}

/** The single main button of a form, disabled while the request runs. */
export function SubmitButton({ label, busy, disabled }: { label: string; busy: boolean; disabled?: boolean }) {
  return (
    <button
      type="submit"
      disabled={busy || disabled}
      className="min-h-12 rounded-lg bg-brand px-4 text-lg font-semibold text-surface disabled:opacity-60"
    >
      {label}
    </button>
  )
}

/** An error message announced to screen readers; renders nothing without a message. */
export function FormMessage({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-lg border border-red-700 bg-surface p-3 text-sm text-red-700">
      {message}
    </p>
  )
}
