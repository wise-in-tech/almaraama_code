import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { components } from '@/shared/api'
import { Button, SelectField, TextField } from '@/shared/ui'

type UnitType = components['schemas']['UnitType']

/** What a new category needs: unit type, readable code, names (at least one language, A6). */
export type CategoryDraft = { unitType: UnitType; code: string; name: { ar?: string; en?: string; fr?: string }; sortOrder: number }

/** The form of a unit category, for an organization or the platform defaults (A6, M7, criterion 2). */
export function CategoryForm({ busy, onSubmit }: { busy: boolean; onSubmit: (draft: CategoryDraft) => void }) {
  const { t } = useTranslation()
  const [unitType, setUnitType] = useState<UnitType>('APARTMENT')
  const [code, setCode] = useState('')
  const [names, setNames] = useState({ ar: '', en: '', fr: '' })
  const name = Object.fromEntries(Object.entries(names).filter(([, value]) => value.trim())) as CategoryDraft['name']
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <SelectField id="category-type" label={t('units.type')} value={unitType} onChange={(e) => setUnitType(e.target.value as UnitType)}
        options={(['APARTMENT', 'SHOP', 'PARKING', 'STORAGE', 'OTHER'] as const).map((value) => ({ value, label: t(`unitType.${value}`) }))} />
      <TextField id="category-code" label={t('categories.code')} hint={t('categories.codeHint')} dir="ltr" value={code}
        onChange={(e) => setCode(e.target.value.toUpperCase())} />
      {(['ar', 'fr', 'en'] as const).map((language) => (
        <TextField key={language} id={`category-name-${language}`} label={`${t('categories.name')} (${t(`language.${language}`)})`}
          dir={language === 'ar' ? 'rtl' : 'ltr'} value={names[language]} onChange={(e) => setNames({ ...names, [language]: e.target.value })} />
      ))}
      <Button tone="primary" busy={busy} disabled={!/^[A-Z0-9][A-Z0-9_-]{0,29}$/.test(code) || Object.keys(name).length === 0}
        onClick={() => onSubmit({ unitType, code, name, sortOrder: 100 })}>
        {t('categories.add')}
      </Button>
    </div>
  )
}
