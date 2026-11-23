import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentLogLevel, log, maskEmail } from '.'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('log (ADR-033)', () => {
  it('ADR033_aTesterRaisesTheLevelForThisBrowserOnly', () => {
    localStorage.setItem('almaraama.log', 'silent')
    expect(currentLogLevel()).toBe('silent')
    const debug = vi.spyOn(console, 'debug').mockImplementation(() => undefined)
    log.debug('test', 'hidden')
    expect(debug).not.toHaveBeenCalled()

    localStorage.setItem('almaraama.log', 'debug')
    log.debug('test', 'shown')
    expect(debug).toHaveBeenCalledWith('[test] shown', '')
  })

  it('ADR033_anUnknownLevelFallsBackToWarn', () => {
    localStorage.setItem('almaraama.log', 'loud')
    expect(currentLogLevel()).toBe('warn')
  })

  it('ADR033_anEmailIsMaskedBeforeLogging', () => {
    expect(maskEmail('amina@example.dz')).toBe('***@example.dz')
  })
})
