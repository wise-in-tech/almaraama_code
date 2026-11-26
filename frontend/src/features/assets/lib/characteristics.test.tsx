import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { coreBlocks, createRegistry, ScreenRenderer } from '@/engine'
import { characteristicsScreen, valuesFromForm } from './characteristics'

const text = (fr: string) => ({ ar: fr, en: fr, fr })
const definitions = [
  { id: '1', scope: 'PLATFORM' as const, familyId: 'f', code: 'capacityKg', dataType: 'INTEGER' as const, required: false,
    label: text('Charge'), unit: 'kg', sortOrder: 10, active: true, version: 0 },
  { id: '2', scope: 'PLATFORM' as const, familyId: 'f', code: 'doorType', dataType: 'LIST' as const, required: false,
    label: text('Type de porte'), options: [{ code: 'AUTOMATIC', label: text('Automatique'), active: true }], sortOrder: 20, active: true, version: 0 },
  { id: '3', scope: 'PLATFORM' as const, familyId: 'f', code: 'old', dataType: 'TEXT' as const, required: false,
    label: text('Ancien'), sortOrder: 30, active: false, version: 0 },
]

describe('"Caractéristiques" of an equipment (G3, ADR-039)', () => {
  it('G3_theCharacteristicsAreRenderedByTheEngineFromTheirDefinitions', () => {
    const definition = characteristicsScreen(text('Caractéristiques'), definitions,
      { capacityKg: 630, doorType: 'AUTOMATIC', old: 'x' }, { yes: text('Oui'), no: text('Non') })
    render(<ScreenRenderer definition={definition} registry={createRegistry(coreBlocks)} />)
    expect(screen.getByText('630 kg')).toBeInTheDocument()
    expect(screen.getByText('Automatique')).toBeInTheDocument()
    expect(screen.queryByText('Ancien')).not.toBeInTheDocument()
  })

  it('G3_typedValuesAreSentWithTheirType', () => {
    expect(valuesFromForm(definitions, { capacityKg: '630', doorType: 'AUTOMATIC', old: ' ' }))
      .toEqual({ capacityKg: 630, doorType: 'AUTOMATIC' })
  })
})
