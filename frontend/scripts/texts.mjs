// ADR-029: the locale files of the frontend are the developers' source of the interface texts; the backend loads a
// copy at start-up into the text dictionary. `node scripts/texts.mjs sync` copies them, `node scripts/texts.mjs check`
// fails when the copies differ (run by `pnpm verify`).
import { copyFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = join(root, 'src/shared/i18n/locales')
const target = join(root, '../backend/modules/customization/impl/src/main/resources/texts')
const languages = ['ar', 'en', 'fr']
const mode = process.argv[2]

if (mode === 'sync') {
  for (const language of languages) copyFileSync(join(source, `${language}.json`), join(target, `${language}.json`))
  console.log(`Texts copied to ${target}`)
} else if (mode === 'check') {
  const stale = languages.filter((language) => {
    const copy = join(target, `${language}.json`)
    return !existsSync(copy) || readFileSync(copy, 'utf8') !== readFileSync(join(source, `${language}.json`), 'utf8')
  })
  if (stale.length > 0) {
    console.error(`Backend texts out of date (${stale.join(', ')}): run pnpm sync:texts`)
    process.exit(1)
  }
} else {
  console.error('Usage: node scripts/texts.mjs sync|check')
  process.exit(2)
}
