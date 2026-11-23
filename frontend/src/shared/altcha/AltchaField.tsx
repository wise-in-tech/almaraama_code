import 'altcha/external'
import 'altcha/altcha.css'
import workerUrl from 'altcha/worker?url'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { log } from '@/shared/log'

/** The challenge endpoint of the API (ADR-032). */
const CHALLENGE_URL = `${globalThis.location?.origin ?? ''}/api/v1/altcha/challenge`

type AltchaState = 'unverified' | 'verifying' | 'verified' | 'expired' | 'error'

/**
 * Anti-robot proof of work of the public forms that send an e-mail (ADR-032): ALTCHA 2.3.0 (MIT), no outside
 * service, nothing to read or click. It starts as soon as the form shows and gives the solution to {@code onPayload}
 * (the value of the {@code X-Altcha} header), or {@code null} when it expires. When the challenge cannot be fetched
 * or solved, a message tells the person what to do. The worker is served by our own domain,
 * so the strict CSP needs no {@code blob:}.
 */
export function AltchaField({ onPayload }: { onPayload: (payload: string | null) => void }) {
  const { t } = useTranslation()
  const widget = useRef<HTMLElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const element = widget.current
    if (!element) return undefined
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ state: AltchaState; payload?: string }>).detail
      log.debug('altcha', `state ${detail.state}`)
      setFailed(detail.state === 'error')
      onPayload(detail.state === 'verified' ? (detail.payload ?? null) : null)
    }
    element.addEventListener('statechange', listener)
    return () => element.removeEventListener('statechange', listener)
  }, [onPayload])

  const strings = JSON.stringify({
    label: t('altcha.label'),
    verifying: t('altcha.verifying'),
    verified: t('altcha.verified'),
    error: t('altcha.error'),
    expired: t('altcha.expired'),
  })

  return (
    <>
    <altcha-widget
      ref={widget}
      challengeurl={CHALLENGE_URL}
      workerurl={workerUrl}
      auto="onload"
      hidefooter
      strings={strings}
    />
    {failed && (
      <p role="alert" className="text-sm text-red-700">
        {t('altcha.failedHelp')}
      </p>
    )}
    </>
  )
}
