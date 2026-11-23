import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { useEffect } from 'react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderPage } from '../test/render'
import { ForgotPasswordPage } from './ForgotPasswordPage'
import { InvitationPage } from './InvitationPage'
import { ResetPasswordPage } from './ResetPasswordPage'
import { VerifyEmailPage } from './VerifyEmailPage'

// The ALTCHA widget needs a Web Worker, absent from jsdom: a stub hands over a proof at once (ADR-032).
vi.mock('@/shared/altcha', () => ({
  AltchaField: ({ onPayload }: { onPayload: (payload: string | null) => void }) => {
    useEffect(() => onPayload('solved-proof'), [onPayload])
    return null
  },
}))

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
beforeEach(() => window.history.replaceState(null, '', '/'))

const SECRET = 'k'.repeat(43)

/** Puts a link secret after `#`, as in the e-mails (ADR-030). */
function openLink(path: string) {
  window.history.replaceState(null, '', `${path}#${SECRET}`)
}

describe('account links and forgotten password (D1, D4, D5)', () => {
  it('D5_forgottenPasswordSendsTheEmailWithTheAltchaProofAndAlwaysConfirms', async () => {
    let altcha: string | null = null
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset', async ({ request }) => {
        altcha = request.headers.get('X-Altcha')
        body = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    renderPage('/forgot-password', ForgotPasswordPage)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Adresse e-mail'), 'amina@example.dz')
    await user.click(screen.getByRole('button', { name: 'Recevoir le lien' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Si un compte correspond à cette adresse')
    expect(altcha).toBe('solved-proof')
    expect(body).toEqual({ email: 'amina@example.dz' })
  })

  it('D5_aResetLinkSetsANewPasswordAndTakesTheSecretOutOfTheAddressBar', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    openLink('/reset-password')
    renderPage('/reset-password', ResetPasswordPage)
    const user = userEvent.setup()
    const password = await screen.findByLabelText('Nouveau mot de passe')
    expect(window.location.hash).toBe('')
    await user.type(password, 'Une autre phrase')
    await user.type(screen.getByLabelText('Confirmez le mot de passe'), 'Une autre phrase')
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Votre mot de passe a été changé')
    expect(sent).toEqual({ token: SECRET, password: 'Une autre phrase' })
  })

  it('D5_aShortOrMismatchedPasswordIsRefusedBeforeTheServer', async () => {
    openLink('/reset-password')
    renderPage('/reset-password', ResetPasswordPage)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Nouveau mot de passe'), 'court')
    await user.type(screen.getByLabelText('Confirmez le mot de passe'), 'autre')
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))
    expect(await screen.findByText('Au moins 8 caractères.')).toBeInTheDocument()
    expect(screen.getByText('Les deux mots de passe ne sont pas identiques.')).toBeInTheDocument()
  })

  it('D5_theServerReasonOfARefusedPasswordIsShown', async () => {
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unprocessable', status: 422, code: 'PASSWORD_TOO_COMMON', requestId: 'r' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    openLink('/reset-password')
    renderPage('/reset-password', ResetPasswordPage)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Nouveau mot de passe'), 'password123')
    await user.type(screen.getByLabelText('Confirmez le mot de passe'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ce mot de passe est trop courant')
  })

  it('D1_aVerificationLinkConfirmsTheEmail', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/v1/accounts/verify-email', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    openLink('/verify-email')
    renderPage('/verify-email', VerifyEmailPage)
    expect(await screen.findByRole('status')).toHaveTextContent('Votre adresse e-mail est confirmée')
    expect(sent).toEqual({ token: SECRET })
  })

  it('D1_withoutALinkANewOneCanBeAskedFor', async () => {
    let altcha: string | null = null
    server.use(
      http.post('*/api/v1/accounts/verify-email/resend', ({ request }) => {
        altcha = request.headers.get('X-Altcha')
        return new HttpResponse(null, { status: 202 })
      }),
    )
    renderPage('/verify-email', VerifyEmailPage)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Adresse e-mail'), 'amina@example.dz')
    await user.click(screen.getByRole('button', { name: 'Recevoir un nouveau lien' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Le lien est valable 48 heures')
    expect(altcha).toBe('solved-proof')
  })

  it('D4_theSetPasswordLinkGreetsThePersonAndSignsThemIn', async () => {
    server.use(
      http.post('*/api/v1/invitations/inspect', () =>
        HttpResponse.json({ firstName: 'Amina', email: 'amina@example.dz', language: 'FR' }),
      ),
      http.post('*/api/v1/invitations/accept', () =>
        HttpResponse.json({ accessToken: 'a.b.c', tokenType: 'Bearer', expiresIn: 600 }),
      ),
    )
    openLink('/invitation')
    renderPage('/invitation', InvitationPage)
    expect(await screen.findByText('Bonjour Amina')).toBeInTheDocument()
    expect(screen.getByText(/amina@example\.dz/)).toBeInTheDocument()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'Une phrase solide')
    await user.type(screen.getByLabelText('Confirmez le mot de passe'), 'Une phrase solide')
    await user.click(screen.getByRole('button', { name: 'Enregistrer mon mot de passe' }))
    expect(await screen.findByText('page /')).toBeInTheDocument()
  })

  it('ADR030_anExpiredLinkSaysSoInThePersonsWords', async () => {
    server.use(
      http.post('*/api/v1/invitations/inspect', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404, code: 'LINK_INVALID_OR_EXPIRED', requestId: 'r' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    openLink('/invitation')
    renderPage('/invitation', InvitationPage)
    expect(await screen.findByRole('alert')).toHaveTextContent("Ce lien n'est plus valable")
  })
})
