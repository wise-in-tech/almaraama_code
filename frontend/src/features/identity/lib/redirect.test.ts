import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('return after sign-in (criterion 8, ASVS V5.1.5)', () => {
  it('criterion8_aScannedLabelOpensAgainAfterSignIn', () => {
    expect(safeRedirect('/q/ABCDEFGHIJKLMNOPQRSTUVWXYZ')).toBe('/q/ABCDEFGHIJKLMNOPQRSTUVWXYZ')
  })

  it('neverFollowsAnotherSite', () => {
    expect(safeRedirect('https://evil.example')).toBe('/')
    expect(safeRedirect('//evil.example')).toBe('/')
    expect(safeRedirect('/\\evil.example')).toBe('/')
    expect(safeRedirect('/login')).toBe('/')
    expect(safeRedirect(undefined)).toBe('/')
  })
})
