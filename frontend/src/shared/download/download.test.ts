import { describe, expect, it } from 'vitest'
import { toCsv } from './index'

describe('CSV of the pre-registration keys (ADR-034)', () => {
  it('quotesEveryCellAndNeutralizesFormulas', () => {
    const csv = toCsv([['Nom', 'Clé'], ['=HYPERLINK("x")', 'AB"CD']])
    expect(csv.startsWith('﻿')).toBe(true)
    expect(csv).toContain(`"'=HYPERLINK(""x"")","AB""CD"`)
  })
})
