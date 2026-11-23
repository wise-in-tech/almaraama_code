/**
 * Technical log of the application (ADR-033). One switch: `VITE_LOG_LEVEL` (`silent`, `warn`, `info`, `debug`;
 * `warn` by default, `debug` in development). On a deployed build, a tester raises it for one browser only with
 * `localStorage["almaraama.log"] = "debug"`. The log never receives a secret, a token, a password or a full e-mail:
 * callers pass identifiers, paths and codes only. ESLint forbids `console` everywhere else.
 */

/** Levels from the quietest to the most detailed. */
export const LOG_LEVELS = ['silent', 'warn', 'info', 'debug'] as const
/** A log level. */
export type LogLevel = (typeof LOG_LEVELS)[number]

const OVERRIDE_KEY = 'almaraama.log'

/** The level of this browser: the local override when valid, else the build's, else `warn`. */
export function currentLogLevel(): LogLevel {
  const override = readOverride()
  const configured = override ?? import.meta.env.VITE_LOG_LEVEL ?? (import.meta.env.DEV ? 'debug' : 'warn')
  return (LOG_LEVELS as readonly string[]).includes(configured) ? (configured as LogLevel) : 'warn'
}

/** The level set for this browser, or {@code null} (storage may be blocked, for example in a private window). */
function readOverride(): string | null {
  try {
    return globalThis.localStorage?.getItem(OVERRIDE_KEY) ?? null
  } catch {
    return null
  }
}

/** Whether a message of this level is written at the current level. */
function enabled(level: Exclude<LogLevel, 'silent'>): boolean {
  return LOG_LEVELS.indexOf(currentLogLevel()) >= LOG_LEVELS.indexOf(level)
}

/** Masks an e-mail for a log line: `amina@example.dz` becomes `***@example.dz`. */
export function maskEmail(email: string): string {
  const at = email.lastIndexOf('@')
  return at < 0 ? '***' : `***${email.slice(at)}`
}

/* eslint-disable no-console -- this module is the only place allowed to write to the console (ADR-033) */
/** The application's logger; every message starts with its area, such as `api` or `engine`. */
export const log = {
  /** Detailed tracing, for testers and developers. */
  debug(area: string, message: string, details?: Record<string, unknown>): void {
    if (enabled('debug')) console.debug(`[${area}] ${message}`, details ?? '')
  },
  /** Notable events. */
  info(area: string, message: string, details?: Record<string, unknown>): void {
    if (enabled('info')) console.info(`[${area}] ${message}`, details ?? '')
  },
  /** Something unexpected that the application recovered from. */
  warn(area: string, message: string, details?: Record<string, unknown>): void {
    if (enabled('warn')) console.warn(`[${area}] ${message}`, details ?? '')
  },
}
/* eslint-enable no-console */
