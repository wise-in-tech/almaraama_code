import { describe, expect, it } from 'vitest'
import { categoryTone, missingInput } from './status'

describe('ticket passages (H5, H6, ADR-036)', () => {
  const hold = { reasonRequired: true, reasons: ['PART', 'QUOTE', 'ACCESS', 'OTHER'], commentRequired: false, commentRequiredForReasons: ['OTHER'] }

  it('H5_onHoldNeedsAReasonAndACommentForOther', () => {
    expect(missingInput(hold, '', '')).toBe('reason')
    expect(missingInput(hold, 'OTHER', ' ')).toBe('comment')
    expect(missingInput(hold, 'OTHER', 'Clé chez le gardien')).toBeNull()
    expect(missingInput(hold, 'PART', '')).toBeNull()
  })

  it('H6_aCancellationByTheManagerNeedsAComment', () => {
    const cancel = { reasonRequired: false, commentRequired: true }
    expect(missingInput(cancel, '', '')).toBe('comment')
    expect(missingInput(cancel, '', 'Doublon')).toBeNull()
  })

  it('shows waiting tickets in warning and finished ones as good', () => {
    expect(categoryTone('ON_HOLD')).toBe('warn')
    expect(categoryTone('CLOSED')).toBe('good')
    expect(categoryTone('CANCELLED')).toBe('neutral')
  })
})
