import type { ScreenDefinition } from '@/engine'
import type { components } from '@/shared/api'
import type { LocalizedText } from '@/shared/i18n'

type Definition = components['schemas']['AttributeDefinition']
type Values = components['schemas']['AttributeValues']

/**
 * The "Caractéristiques" block of an equipment (G3, ADR-039, ADR-021) as a screen definition: data rendered by the
 * engine's closed registry, never code. Only active definitions with a value are shown; a list shows the label of its
 * option, a yes/no its translated word.
 */
export function characteristicsScreen(
  title: LocalizedText,
  definitions: Definition[],
  values: Values,
  yesNo: { yes: LocalizedText; no: LocalizedText },
): ScreenDefinition {
  const fields = definitions
    .filter((d) => d.active && values[d.code] !== undefined && values[d.code] !== '')
    .map((d) => {
      const raw = values[d.code]
      let value: string | number | LocalizedText
      if (d.dataType === 'LIST') value = d.options?.find((o) => o.code === raw)?.label ?? String(raw)
      else if (d.dataType === 'BOOLEAN') value = raw === true ? yesNo.yes : yesNo.no
      else value = typeof raw === 'number' ? (d.unit ? `${raw} ${d.unit}` : raw) : String(raw)
      return { label: d.label, value }
    })
  return { screen: 'asset-characteristics', version: 1, blocks: [{ type: 'FieldGroup', title, fields }] }
}

/**
 * The values typed in the form, converted to the types the server expects (G3): numbers for INTEGER and DECIMAL,
 * booleans for BOOLEAN, text otherwise; empty fields are left out.
 */
export function valuesFromForm(definitions: Definition[], typed: Record<string, string>): Values {
  const values: Values = {}
  for (const d of definitions) {
    const text = (typed[d.code] ?? '').trim()
    if (text === '') continue
    if (d.dataType === 'INTEGER' || d.dataType === 'DECIMAL') values[d.code] = Number(text.replace(',', '.'))
    else if (d.dataType === 'BOOLEAN') values[d.code] = text === 'true'
    else values[d.code] = text
  }
  return values
}
