import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { i18n } from '@/shared/i18n'
import { coreBlocks, createRegistry, parseScreenDefinition, ScreenRenderer, type ScreenDefinition } from '.'

const registry = createRegistry(coreBlocks)

function assetSheet(blocks: unknown[]): ScreenDefinition {
  const parsed = parseScreenDefinition({
    screen: 'asset.detail',
    version: 1,
    title: { ar: 'المصعد، العمارة B', en: 'Lift, building B', fr: 'Ascenseur, bâtiment B' },
    blocks,
  })
  if (!parsed.success) throw new Error(parsed.error.message)
  return parsed.data
}

const characteristics = {
  type: 'FieldGroup',
  title: { ar: 'الخصائص', en: 'Characteristics', fr: 'Caractéristiques' },
  fields: [
    { label: { ar: 'الطراز', en: 'Model', fr: 'Modèle' }, value: 'KX-8' },
    { label: { ar: 'العائلة', en: 'Family', fr: 'Famille' }, value: { ar: 'مصعد', en: 'Lift', fr: 'Ascenseur' } },
  ],
}

describe('ScreenRenderer', () => {
  afterEach(async () => {
    await act(() => i18n.changeLanguage('fr'))
  })

  it('renders a registered block in the current language', async () => {
    render(<ScreenRenderer definition={assetSheet([characteristics])} registry={registry} />)
    expect(screen.getByText('Caractéristiques')).toBeInTheDocument()
    expect(screen.getByText('Ascenseur')).toBeInTheDocument()

    await act(() => i18n.changeLanguage('ar'))
    expect(screen.getByText('الخصائص')).toBeInTheDocument()
    expect(document.documentElement.dir).toBe('rtl')

    await act(() => i18n.changeLanguage('en'))
    expect(screen.getByText('Characteristics')).toBeInTheDocument()
    expect(screen.getByText('Lift')).toBeInTheDocument()
    expect(document.documentElement.dir).toBe('ltr')
  })

  it('refuses a component that is not in the registry, and executes nothing', () => {
    const { container } = render(
      <ScreenRenderer
        definition={assetSheet([{ type: 'CustomHtml', html: '<script>window.pwned = true</script>' }])}
        registry={registry}
      />,
    )
    expect(container.querySelector('[data-refused-block="CustomHtml"]')).not.toBeNull()
    expect(container.querySelector('script')).toBeNull()
    expect((window as unknown as { pwned?: boolean }).pwned).toBeUndefined()
  })

  it('refuses a registered block whose definition does not match its schema', () => {
    const { container } = render(
      <ScreenRenderer definition={assetSheet([{ type: 'FieldGroup', fields: 'not a list' }])} registry={registry} />,
    )
    expect(container.querySelector('[data-refused-block="FieldGroup"]')).not.toBeNull()
  })

  it('shows values as text, never as HTML', () => {
    const payload = '<img src=x onerror="window.pwned=true">'
    const { container } = render(
      <ScreenRenderer
        definition={assetSheet([
          { ...characteristics, fields: [{ label: { ar: 'ملاحظة', en: 'Note', fr: 'Note' }, value: payload }] },
        ])}
        registry={registry}
      />,
    )
    expect(screen.getByText(payload)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('rejects a definition without blocks before rendering', () => {
    expect(parseScreenDefinition({ screen: 'x', version: 1 }).success).toBe(false)
  })
})
