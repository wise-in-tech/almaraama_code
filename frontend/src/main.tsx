import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-sans-arabic/400.css'
import '@fontsource/ibm-plex-sans-arabic/600.css'
import './app/styles.css'
import '@/shared/i18n'
import { startTextLoader } from '@/shared/i18n/remote-texts'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'

/** `pnpm dev:mocks` only. Guarded by DEV so neither the mocks nor their worker reach a production build. */
async function enableApiMocks(): Promise<void> {
  if (import.meta.env.DEV && import.meta.env.VITE_API_MOCKS === 'true') {
    const { network } = await import('virtual:msw')
    const { handlers } = await import('./mocks/handlers')
    network.configure({ handlers })
    await network.enable()
  }
}

const container = document.getElementById('root')
if (!container) {
  throw new Error('Missing #root element in index.html')
}

void enableApiMocks().then(() => {
  // ADR-029: embedded texts first, then the dictionary of the server, revalidated in the background.
  startTextLoader()
  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
