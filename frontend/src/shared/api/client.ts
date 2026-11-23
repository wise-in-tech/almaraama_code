import createClient, { type Middleware } from 'openapi-fetch'
import { log } from '@/shared/log'
import { accessTokenExpiresWithin, getAccessToken } from './access-token'
import type { paths } from './schema'
import { refreshSession, SESSION_ENDED_EVENT } from './session'

const RENEW_MARGIN_MS = 30_000

/**
 * Whether the request targets the public endpoints (sign-in, account links, anti-robot challenge), which must never
 * trigger a session renewal nor end a session (ADR-030, ADR-032).
 */
function isAuthCall(url: string): boolean {
  return ['/v1/auth/', '/v1/invitations/', '/v1/accounts/', '/v1/altcha/'].some((path) => url.includes(path))
}

/** Renews the access token shortly before it expires, then sends it with every API call. */
const authentication: Middleware = {
  async onRequest({ request }) {
    if (isAuthCall(request.url)) {
      return request
    }
    if (getAccessToken() !== null && accessTokenExpiresWithin(RENEW_MARGIN_MS)) {
      await refreshSession()
    }
    const token = getAccessToken()
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
  onResponse({ request, response }) {
    // ADR-033: method, path, status and request id only; never a body or a header value.
    log.debug('api', `${request.method} ${new URL(request.url).pathname} → ${response.status}`, {
      requestId: response.headers.get('X-Request-Id'),
    })
    if (response.status === 401 && !isAuthCall(request.url)) {
      window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
    }
    return response
  },
}

/**
 * Typed client generated from api/openapi.yaml (contract first). Paths, parameters and bodies are checked by the
 * compiler: a call that does not match the contract does not build. Same origin: the proxy routes /api.
 */
export const api = createClient<paths>({
  baseUrl: `${globalThis.location?.origin ?? ''}/api`,
  // Looked up at each call, so request interception (MSW mocks, tests) installed later still applies.
  fetch: (request) => globalThis.fetch(request),
})
api.use(authentication)
