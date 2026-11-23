import { describe, expect, it } from 'vitest'
import { phoneCountries, toE164 } from './phone'

describe('toE164 (D1, ADR-028)', () => {
  it('reads the national form of the chosen country', () => {
    expect(toE164('DZ', '0555 12 34 56')).toBe('+213555123456')
    expect(toE164('FR', '06 12 34 56 78')).toBe('+33612345678')
  })

  it('lets a full international number win over the chosen country', () => {
    expect(toE164('DZ', '+33 6 12 34 56 78')).toBe('+33612345678')
    expect(toE164('DZ', '00971 50 123 4567')).toBe('+971501234567')
  })

  it('refuses what is not a number of that country', () => {
    expect(toE164('DZ', '0123')).toBeNull()
    expect(toE164('DZ', 'phone')).toBeNull()
  })
})

describe('phoneCountries', () => {
  it('lists every country with its prefix, Algeria first, named in the interface language', () => {
    const french = phoneCountries('fr')
    expect(french.length).toBeGreaterThan(200)
    expect(french[0]).toMatchObject({ code: 'DZ', callingCode: '213', name: 'Algérie' })
    expect(phoneCountries('en')[0]?.name).toBe('Algeria')
  })
})
