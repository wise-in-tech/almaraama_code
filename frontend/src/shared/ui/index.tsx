import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { forwardRef } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

/*
 * Small building blocks of the screens (ADR-024): large touch targets, plain words, logical spacing (ms-, me-) so the
 * same markup works left-to-right and right-to-left. Text is always rendered as text, never as HTML.
 */

type Tone = 'primary' | 'secondary' | 'danger'

const tones: Record<Tone, string> = {
  primary: 'bg-brand text-surface',
  secondary: 'border border-line bg-surface text-ink',
  danger: 'border border-red-700 bg-surface text-red-700',
}

/** A button; {@code tone} picks its weight, {@code busy} disables it while a request runs. */
export function Button({
  tone = 'secondary',
  busy = false,
  className,
  disabled,
  type = 'button',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: Tone; busy?: boolean }) {
  return (
    <button
      type={type}
      disabled={busy || disabled}
      className={cn('min-h-11 rounded-lg px-4 text-sm font-semibold disabled:opacity-60', tones[tone], className)}
      {...rest}
    />
  )
}

type FieldShell = { id: string; label: string; error?: string | null | undefined; hint?: string | undefined }

/** Label, control, hint and error of a form field, linked for screen readers. */
function FieldFrame({ id, label, error, hint, children }: FieldShell & { children: ReactNode }) {
  return (
    <div className="grid gap-1">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      {children}
      {hint && <p className="text-xs text-muted">{hint}</p>}
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}

const control = 'min-h-11 rounded-lg border border-line bg-surface px-3 focus:border-brand focus:outline-none'

/** A labelled text input; set {@code dir="ltr"} for e-mails, codes and numbers. */
export const TextField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & FieldShell>(
  function TextField({ id, label, error, hint, className, ...input }, ref) {
    return (
      <FieldFrame id={id} label={label} error={error} hint={hint}>
        <input
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className={cn(control, error && 'border-red-700', className)}
          {...input}
        />
      </FieldFrame>
    )
  },
)

/** A labelled multi-line text input. */
export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & FieldShell>(
  function TextArea({ id, label, error, hint, className, ...input }, ref) {
    return (
      <FieldFrame id={id} label={label} error={error} hint={hint}>
        <textarea
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          className={cn(control, 'min-h-24 py-2', error && 'border-red-700', className)}
          {...input}
        />
      </FieldFrame>
    )
  },
)

/** A labelled select with its options. */
export const SelectField = forwardRef<
  HTMLSelectElement,
  SelectHTMLAttributes<HTMLSelectElement> & FieldShell & { options: { value: string; label: string }[] }
>(function SelectField({ id, label, error, hint, options, className, ...select }, ref) {
  return (
    <FieldFrame id={id} label={label} error={error} hint={hint}>
      <select ref={ref} id={id} className={cn(control, className)} {...select}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldFrame>
  )
})

/** A labelled checkbox. */
export const CheckboxField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { id: string; label: string }>(
  function CheckboxField({ id, label, ...input }, ref) {
    return (
      <label htmlFor={id} className="flex min-h-11 items-center gap-2 text-sm">
        <input ref={ref} id={id} type="checkbox" className="size-5 accent-brand" {...input} />
        {label}
      </label>
    )
  },
)

/** The title of a page, with an optional line of explanation and actions. */
export function PageHeader({ title, intro, actions }: { title: string; intro?: string; actions?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-3">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold">{title}</h1>
        {intro && <p className="text-muted">{intro}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </header>
  )
}

/** A white panel grouping related content. */
export function Card({ title, children, className }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={cn('grid gap-3 rounded-xl border border-line bg-surface p-4', className)}>
      {title && <h2 className="text-lg font-semibold">{title}</h2>}
      {children}
    </section>
  )
}

/** An error announced to screen readers; nothing without a message. */
export function ErrorMessage({ message }: { message: string | null | undefined }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-lg border border-red-700 bg-surface p-3 text-sm text-red-700">
      {message}
    </p>
  )
}

/** A confirmation announced to screen readers; nothing without a message. */
export function Notice({ message }: { message: string | null | undefined }) {
  if (!message) return null
  return (
    <p role="status" className="rounded-lg border border-brand bg-brand-soft p-3 text-sm">
      {message}
    </p>
  )
}

/** A short status label. */
export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'good' | 'warn' }) {
  const colors = { neutral: 'bg-ground text-ink', good: 'bg-brand-soft text-brand', warn: 'bg-amber-100 text-amber-900' }
  return <span className={cn('rounded-full px-2 py-0.5 text-xs font-semibold', colors[tone])}>{children}</span>
}

/** What a list shows when it has nothing. */
export function EmptyState({ message }: { message: string }) {
  return <p className="rounded-lg border border-dashed border-line p-4 text-center text-muted">{message}</p>
}

/** A simple table: header labels and rows of cells; scrolls sideways on a small phone. */
export function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface">
      <table className="w-full text-start text-sm">
        <thead className="bg-ground">
          <tr>
            {headers.map((header) => (
              <th key={header} scope="col" className="px-3 py-2 text-start font-semibold">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((cells, index) => (
            <tr key={index} className="border-t border-line align-top">
              {cells.map((cell, column) => (
                <td key={column} className="px-3 py-2">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Shown while a page loads its data. */
export function Loading() {
  const { t } = useTranslation()
  return (
    <p role="status" aria-live="polite" className="text-muted">
      {t('common.loading')}
    </p>
  )
}

export { ImportReportView, type ImportReport } from './ImportReport'
