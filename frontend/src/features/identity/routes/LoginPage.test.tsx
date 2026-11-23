import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { getAccessToken } from '@/shared/api'
import { renderPage } from '../test/render'
import { LoginPage } from './LoginPage'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

/** Types the e-mail and password, then presses the button. */
async function signIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Adresse e-mail'), email)
  await user.type(screen.getByLabelText('Mot de passe'), password)
  await user.click(screen.getByRole('button', { name: 'Se connecter' }))
}

/** A problem answer of the API. */
function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { type: 'about:blank', title: 'Problem', status, code, requestId: 'test', ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

describe('LoginPage (D5, ADR-030)', () => {
  it('D5_signsInWithEmailAndPasswordAndKeepsTheTokenInMemoryOnly', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ accessToken: 'header.payload.signature', tokenType: 'Bearer', expiresIn: 600 })
      }),
    )
    renderPage('/login', LoginPage)
    await signIn('amina@example.dz', 'Une phrase solide')

    expect(await screen.findByText('page /')).toBeInTheDocument()
    expect(sent).toEqual({ email: 'amina@example.dz', password: 'Une phrase solide' })
    expect(getAccessToken()).toBe('header.payload.signature')
    expect(JSON.stringify(localStorage)).not.toContain('header.payload.signature')
  })

  it('D5_showsTheTranslatedMessageOfTheErrorCode', async () => {
    server.use(http.post('*/api/v1/auth/login', () => problem(429, 'AUTH_TEMPORARILY_LOCKED', { retryAfterSeconds: 300 })))
    renderPage('/login', LoginPage)
    await signIn('amina@example.dz', 'wrong password')

    expect(await screen.findByRole('alert')).toHaveTextContent("Trop d'essais. Réessayez dans 5 minute(s).")
  })

  it('D1_anUnverifiedEmailIsOfferedANewConfirmationLink', async () => {
    server.use(http.post('*/api/v1/auth/login', () => problem(403, 'EMAIL_NOT_VERIFIED')))
    renderPage('/login', LoginPage)
    await signIn('amina@example.dz', 'Une phrase solide')

    expect(await screen.findByRole('alert')).toHaveTextContent("n'est pas encore confirmée")
    await userEvent.setup().click(screen.getByRole('link', { name: 'Recevoir un nouveau lien de confirmation' }))
    expect(await screen.findByText('page /verify-email')).toBeInTheDocument()
  })

  it('D5_checksTheEmailBeforeCallingTheServer', async () => {
    renderPage('/login', LoginPage)
    await signIn('amina', 'x')
    expect(await screen.findByText("Cette adresse e-mail n'est pas valide.")).toBeInTheDocument()
  })

  it('D5_leadsToTheForgottenPasswordPage', async () => {
    renderPage('/login', LoginPage)
    await userEvent.setup().click(await screen.findByRole('link', { name: 'Mot de passe oublié ?' }))
    expect(await screen.findByText('page /forgot-password')).toBeInTheDocument()
  })

  it('ASVS_V2_1_12_thePasswordCanBeShown', async () => {
    renderPage('/login', LoginPage)
    const input = await screen.findByLabelText('Mot de passe')
    expect(input).toHaveAttribute('type', 'password')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Afficher le mot de passe' }))
    expect(input).toHaveAttribute('type', 'text')
  })
})
