import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

/** Shown for unknown paths, with a link back home. */
export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <section className="grid gap-2">
      <h1 className="text-2xl font-semibold">{t('notFound.title')}</h1>
      <Link to="/" className="text-brand underline">
        {t('notFound.back')}
      </Link>
    </section>
  )
}
