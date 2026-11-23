import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import { i18n } from '@/shared/i18n'

// Arabic is the default interface language; tests start in French so their labels stay stable. A test that
// checks another language switches to it explicitly.
beforeEach(async () => {
  await i18n.changeLanguage('fr')
})

// Vitest runs without globals, so Testing Library's automatic cleanup is registered here.
afterEach(cleanup)
