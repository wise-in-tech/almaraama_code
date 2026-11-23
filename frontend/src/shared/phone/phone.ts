import { getCountries, getCountryCallingCode, parsePhoneNumberFromString, type CountryCode } from 'libphonenumber-js/min'

/** Country assumed when nobody chose one (ADR-028). The server uses the same default. */
export const DEFAULT_PHONE_COUNTRY: CountryCode = 'DZ'

/** A country of the phone picker: ISO code, international prefix and name in the interface language. */
export type PhoneCountry = { code: CountryCode; callingCode: string; name: string }

/**
 * Every country with its international prefix, named in the interface language and sorted by name. The default
 * country comes first, since most people use it.
 */
export function phoneCountries(language: string): PhoneCountry[] {
  let names: Intl.DisplayNames | undefined
  try {
    names = new Intl.DisplayNames([language], { type: 'region' })
  } catch {
    names = undefined
  }
  const countries = getCountries().map((code) => ({
    code,
    callingCode: getCountryCallingCode(code),
    name: names?.of(code) ?? code,
  }))
  countries.sort((a, b) => a.name.localeCompare(b.name, language))
  const index = countries.findIndex((country) => country.code === DEFAULT_PHONE_COUNTRY)
  if (index > 0) countries.unshift(...countries.splice(index, 1))
  return countries
}

/** Whether the value is a country code known to the phone numbering plans. */
export function isPhoneCountry(value: string): value is CountryCode {
  return (getCountries() as string[]).includes(value)
}

/**
 * The number in E.164 form (`+213661234567`), or `null` when it is not a valid number. The person may type the
 * national form of the chosen country (`0661 23 45 67`) or a full international number (`+33 6…`), which wins over
 * the chosen country. The server checks again, and also that it is a mobile.
 */
export function toE164(country: string, typed: string): string | null {
  const region = isPhoneCountry(country) ? country : DEFAULT_PHONE_COUNTRY
  const number = parsePhoneNumberFromString(typed.trim().replace(/^00/, '+'), region)
  return number?.isValid() ? number.number : null
}
