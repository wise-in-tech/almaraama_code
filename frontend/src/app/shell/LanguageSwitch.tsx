import { useTranslation } from 'react-i18next'
import { LANGUAGES, type Language } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'

/** Buttons to switch the interface language, in the order Arabic, English, French (M1). */
export function LanguageSwitch() {
  const { t, i18n } = useTranslation()
  return (
    <div role="group" aria-label={t('language.label')} className="flex gap-1">
      {LANGUAGES.map((language: Language) => (
        <button
          key={language}
          type="button"
          lang={language}
          aria-pressed={i18n.language === language}
          onClick={() => void i18n.changeLanguage(language)}
          className={cn(
            'rounded-md border px-3 py-1 text-sm',
            i18n.language === language ? 'border-brand bg-brand-soft font-semibold text-brand' : 'border-line',
          )}
        >
          {t(`language.${language}`)}
        </button>
      ))}
    </div>
  )
}
