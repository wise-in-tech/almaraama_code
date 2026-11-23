import { log } from '@/shared/log'
import { i18n, LANGUAGES, type Language } from './index'

/**
 * Dynamic text loader (M6, criterion 20, ADR-029). The texts embedded in the release show at once; then the last copy
 * received from the server (local storage), then a background revalidation of the displayed language only: at start,
 * when the application comes back to the foreground and every 15 minutes, with {@code If-None-Match} so that an
 * unchanged dictionary costs a {@code 304}. A text edited by an administrator is thus visible without deployment.
 * Fallback order: chosen language, Arabic (i18next {@code fallbackLng}), embedded text, key.
 */

const REVALIDATE_EVERY_MS = 15 * 60 * 1000
const STORAGE_PREFIX = 'almaraama.texts.'

/** What is kept locally for one language: the server's {@code ETag} and the flat texts. */
type StoredTexts = { etag: string; texts: Record<string, string> }

/** Nested texts as i18next stores them, from the server's flat keys ({@code a.b.c}). */
export type NestedTexts = { [key: string]: string | NestedTexts }

/**
 * Turns flat dotted keys into nested objects. A key that would clash with an existing text (a value and an object at
 * the same place) is skipped: the embedded text stays.
 */
export function unflatten(flat: Record<string, string>): NestedTexts {
  const root: NestedTexts = {}
  for (const [key, value] of Object.entries(flat)) {
    const parts = key.split('.')
    let node: NestedTexts = root
    let clash = false
    for (const part of parts.slice(0, -1)) {
      const next = node[part]
      if (next === undefined) {
        const created: NestedTexts = {}
        node[part] = created
        node = created
      } else if (typeof next === 'string') {
        clash = true
        break
      } else {
        node = next
      }
    }
    const leaf = parts.at(-1)
    if (!clash && leaf !== undefined && typeof node[leaf] !== 'object') {
      node[leaf] = value
    }
  }
  return root
}

/** The local copy of a language, or nothing when absent, unreadable or storage is blocked. */
function readStored(language: Language): StoredTexts | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + language)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StoredTexts>
    return typeof parsed.etag === 'string' && typeof parsed.texts === 'object' && parsed.texts !== null
      ? { etag: parsed.etag, texts: parsed.texts }
      : null
  } catch {
    return null
  }
}

/** Keeps the copy for the next start; silently skipped when storage is blocked or full. */
function writeStored(language: Language, stored: StoredTexts): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + language, JSON.stringify(stored))
  } catch {
    // Private mode or full storage: the texts will simply be fetched again.
  }
}

/** Adds the texts over the embedded ones (deep merge, overwrite), then re-renders the screens. */
function apply(language: Language, texts: Record<string, string>): void {
  i18n.addResourceBundle(language, 'translation', unflatten(texts), true, true)
  if (i18n.language === language) {
    void i18n.changeLanguage(language)
  }
}

/**
 * Asks the server whether the texts of a language changed since the local copy, and applies them when they did.
 * Network errors are only logged: the embedded or local texts stay.
 */
export async function revalidateTexts(language: Language): Promise<void> {
  const stored = readStored(language)
  try {
    const response = await globalThis.fetch(`${globalThis.location?.origin ?? ''}/api/v1/texts/${language.toUpperCase()}`, {
      headers: stored ? { 'If-None-Match': stored.etag } : {},
    })
    if (response.status === 304) {
      log.debug('texts', `${language} unchanged`)
      return
    }
    if (!response.ok) {
      log.warn('texts', `${language} not refreshed (${response.status})`)
      return
    }
    const body = (await response.json()) as { texts?: Record<string, string> }
    const etag = response.headers.get('ETag')
    if (!body.texts) return
    if (etag) writeStored(language, { etag, texts: body.texts })
    apply(language, body.texts)
    log.debug('texts', `${language} refreshed`)
  } catch (error) {
    log.warn('texts', `${language} not refreshed`, { error: String(error) })
  }
}

/** The displayed language as one of ours. */
function current(): Language {
  return LANGUAGES.find((language) => language === i18n.language) ?? 'ar'
}

/**
 * Starts the loader once, at application start: applies the local copy of the displayed language, revalidates it,
 * then again on return to the foreground, every 15 minutes and when the person changes language.
 */
export function startTextLoader(): void {
  const showStored = (language: Language) => {
    const stored = readStored(language)
    if (stored) apply(language, stored.texts)
  }
  showStored(current())
  void revalidateTexts(current())
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') void revalidateTexts(current())
  })
  setInterval(() => void revalidateTexts(current()), REVALIDATE_EVERY_MS)
  let last = current()
  i18n.on('languageChanged', () => {
    const language = current()
    if (language === last) return
    last = language
    showStored(language)
    void revalidateTexts(language)
  })
}
