import i18n from 'i18next'
import { initReactI18next, useTranslation } from 'react-i18next'
import ar from './locales/ar.json'
import en from './locales/en.json'
import fr from './locales/fr.json'

/** Interface languages (M1), in display order: Arabic first, then English, then French. */
export const LANGUAGES = ['ar', 'en', 'fr'] as const
/** An interface language code: `ar`, `en` or `fr`. */
export type Language = (typeof LANGUAGES)[number]

/** Language used when the person has not chosen one yet. */
export const DEFAULT_LANGUAGE: Language = 'ar'

/** Text in every supported language, as defined by `LocalizedText` in api/openapi.yaml. */
export type LocalizedText = Record<Language, string>

const STORAGE_KEY = 'almaraama.language'

/** A supported language from any value, Arabic by default. */
function toLanguage(value: string | null | undefined): Language {
  return LANGUAGES.find((language) => language === value) ?? DEFAULT_LANGUAGE
}

/** The language chosen earlier on this device, or the default when none or when storage is blocked. */
function storedLanguage(): Language {
  try {
    return toLanguage(localStorage.getItem(STORAGE_KEY))
  } catch {
    return DEFAULT_LANGUAGE
  }
}

/** Sets `lang` and `dir` on <html>: Arabic switches the whole layout to right-to-left. */
function applyToDocument(language: Language): void {
  document.documentElement.lang = language
  document.documentElement.dir = i18n.dir(language)
}

void i18n.use(initReactI18next).init({
  resources: { ar: { translation: ar }, en: { translation: en }, fr: { translation: fr } },
  lng: storedLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: LANGUAGES,
  initAsync: false,
  // React already escapes every value it renders.
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (value) => {
  const language = toLanguage(value)
  applyToDocument(language)
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Private mode or blocked storage: the choice simply is not remembered.
  }
})

applyToDocument(toLanguage(i18n.language))

/** Current language and a function that picks the right text from a `LocalizedText`. */
export function useLocalizedText(): { language: Language; localize: (text: LocalizedText) => string } {
  const { i18n: instance } = useTranslation()
  const language = toLanguage(instance.language)
  return { language, localize: (text) => text[language] }
}

export { i18n }
