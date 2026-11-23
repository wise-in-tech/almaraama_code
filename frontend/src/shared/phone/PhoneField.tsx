import { useMemo, type SelectHTMLAttributes, type InputHTMLAttributes } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { phoneCountries } from './phone'

type PhoneFieldProps = {
  id: string
  label: string
  countryLabel: string
  error?: string | undefined
  /** Props of the country select, typically from `register('phoneCountry')`. */
  country: SelectHTMLAttributes<HTMLSelectElement>
  /** Props of the number input, typically from `register('phone')`. */
  number: InputHTMLAttributes<HTMLInputElement>
}

/**
 * Phone number of any country (ADR-028): the country with its international prefix, then the number. Digits stay
 * left-to-right in Arabic too. The form turns both into E.164 with `toE164` before sending.
 */
export function PhoneField({ id, label, countryLabel, error, country, number }: PhoneFieldProps) {
  const { i18n } = useTranslation()
  const countries = useMemo(() => phoneCountries(i18n.language), [i18n.language])
  const control = 'min-h-12 rounded-lg border border-line bg-surface px-3 text-lg focus:border-brand focus:outline-none'

  return (
    <fieldset className="grid gap-1">
      <legend className="mb-1 text-sm font-semibold">{label}</legend>
      <div className="grid grid-cols-[minmax(0,9rem)_minmax(0,1fr)] gap-2">
        <select id={`${id}-country`} aria-label={countryLabel} className={cn(control, 'text-base')} {...country}>
          {countries.map((option) => (
            <option key={option.code} value={option.code}>
              {`${option.name} +${option.callingCode}`}
            </option>
          ))}
        </select>
        <input
          id={id}
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          dir="ltr"
          aria-label={label}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className={cn(control, 'tracking-wide', error && 'border-red-700')}
          {...number}
        />
      </div>
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </fieldset>
  )
}
