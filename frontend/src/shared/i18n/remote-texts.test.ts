import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { i18n } from './index'
import { revalidateTexts, unflatten } from './remote-texts'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
beforeEach(() => localStorage.clear())

describe('dynamic texts (M6, criterion 20, ADR-029)', () => {
  it('ADR029_flatKeysBecomeNestedAndNeverReplaceABranch', () => {
    expect(unflatten({ 'a.b': 'x', 'a.c.d': 'y', 'a.b.e': 'clash' })).toEqual({ a: { b: 'x', c: { d: 'y' } } })
  })

  it('criterion20_anEditedTextIsShownWithoutDeploymentAndRevalidatedWithItsETag', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.get('*/api/v1/texts/FR', ({ request }) => {
        seen.push(request.headers.get('If-None-Match'))
        if (request.headers.get('If-None-Match') === '"v2"') return new HttpResponse(null, { status: 304 })
        return HttpResponse.json(
          { language: 'FR', version: 'v2', texts: { 'session.greeting': 'Salut {{name}} !' } },
          { headers: { ETag: '"v2"' } },
        )
      }),
    )
    await revalidateTexts('fr')
    expect(i18n.t('session.greeting', { name: 'Amina', lng: 'fr' })).toBe('Salut Amina !')
    await revalidateTexts('fr')
    expect(seen).toEqual([null, '"v2"'])
    expect(i18n.t('login.title', { lng: 'fr' })).toBe('Connexion')
  })

  it('ADR029_aNetworkFailureKeepsTheEmbeddedTexts', async () => {
    server.use(http.get('*/api/v1/texts/EN', () => HttpResponse.error()))
    await revalidateTexts('en')
    expect(i18n.t('login.title', { lng: 'en' })).toBe('Sign in')
  })
})
