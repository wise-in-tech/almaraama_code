import type { RequestHandler } from 'msw'

/**
 * Mocks of endpoints defined in api/openapi.yaml but not implemented by the backend yet (contract first).
 * Each handler answers with data shaped like the contract, typed from the generated schema, for example:
 *
 *   http.get('/api/v1/tickets/:id', () => HttpResponse.json<components['schemas']['TicketDetail']>({...}))
 *
 * Remove a handler when the backend implements its endpoint: the dev proxy then reaches the real API.
 */
export const handlers: RequestHandler[] = []
