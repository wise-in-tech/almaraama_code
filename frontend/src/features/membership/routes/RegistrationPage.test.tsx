import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRootRoute, createRoute, createRouter, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { useEffect } from 'react'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { RegistrationPage } from './RegistrationPage'

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

/** Renders the public registration page as opened from a QR code. */
function openFromQrCode(code: string) {
  window.history.replaceState(null, '', `/r#${code}`)
  const root = createRootRoute()
  const page = createRoute({ getParentRoute: () => root, path: '/r', component: RegistrationPage })
  const stub = createRoute({ getParentRoute: () => root, path: '/me/accesses', component: () => <p>accesses</p> })
  const router = createRouter({ routeTree: root.addChildren([page, stub]), history: createMemoryHistory({ initialEntries: ['/r'] }) })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('registration by QR code (D2, D3, ADR-034)', () => {
  it('D2_D3_theFormSendsTheCodeCommentKeyAndAltchaProofThenAsksToCheckTheMailbox', async () => {
    let altcha: string | null = null
    let body: Record<string, unknown> = {}
    server.use(
      http.post('*/api/v1/registration-qr/inspect', () =>
        HttpResponse.json({ siteName: 'Les Pins', organizationName: 'Promo Alger' })),
      http.post('*/api/v1/registrations', async ({ request }) => {
        altcha = request.headers.get('X-Altcha')
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ status: 'CHECK_YOUR_EMAIL' }, { status: 202 })
      }),
    )
    openFromQrCode('K7QM4XPA2R')
    expect(await screen.findByText('Résidence Les Pins (Promo Alger)')).toBeInTheDocument()
    expect(window.location.hash).toBe('')

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Prénom'), 'Samia')
    await user.type(screen.getByLabelText('Nom'), 'Inscrite')
    await user.type(screen.getByLabelText('Adresse e-mail'), 'samia@example.dz')
    await user.type(screen.getByLabelText('Téléphone mobile'), '0661234567')
    await user.type(screen.getByLabelText('Commentaire pour le gestionnaire'), 'Appartement B12, propriétaire')
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'Une résidence bien tenue')
    await user.type(screen.getByLabelText('Clé personnelle (si vous en avez reçu une)'), '4XPA-K7QM-9RTW')
    await user.click(screen.getByRole('button', { name: 'Envoyer mon inscription' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Nous vous avons envoyé un e-mail')
    expect(altcha).toBe('solved-proof')
    expect(body).toMatchObject({
      code: 'K7QM4XPA2R',
      email: 'samia@example.dz',
      phone: '+213661234567',
      comment: 'Appartement B12, propriétaire',
      preRegistrationKey: '4XPA-K7QM-9RTW',
    })
  })

  it('D2_anInvalidCodeIsSaidBeforeTheFormIsSent', async () => {
    server.use(
      http.post('*/api/v1/registration-qr/inspect', () =>
        HttpResponse.json({ type: 'about:blank', title: 'Not Found', status: 404, code: 'REGISTRATION_CODE_INVALID', requestId: 'r' }, { status: 404 })),
    )
    openFromQrCode('ZZZZZZZZZZ')
    expect(await screen.findByText(/Ce code n'est pas valable/)).toBeInTheDocument()
  })
})
