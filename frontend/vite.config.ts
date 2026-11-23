/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { msw } from 'msw/vite'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// ADR-024. Single-page app served as static files by the proxy, on the same origin as /api.
export default defineConfig(() => ({
  plugins: [
    react(),
    tailwindcss(),
    // API mocks of the contract (contract first). The plugin is always loaded so `virtual:msw` resolves, but mocks
    // only start with `pnpm dev:mocks` (VITE_API_MOCKS=true, see main.tsx). The import is guarded by
    // import.meta.env.DEV, so neither the mocks nor the worker script reach a production build.
    msw(),
    VitePWA({
      registerType: 'prompt',
      // External registration script: no inline script, compatible with a strict CSP.
      injectRegister: 'script',
      manifest: {
        name: 'almaraama',
        short_name: 'almaraama',
        description: 'Maintenance des résidences',
        lang: 'ar',
        dir: 'rtl',
        start_url: '/',
        display: 'standalone',
        background_color: '#f4f6f5',
        theme_color: '#1d6c75',
        icons: [{ src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
      workbox: {
        // App shell plus the Latin and Arabic font files, so the app opens offline in both languages.
        globPatterns: [
          '**/*.{js,css,html,svg,webmanifest}',
          '**/ibm-plex-sans-latin-*.woff2',
          '**/ibm-plex-sans-arabic-arabic-*.woff2',
        ],
        // The API is never served from the service worker cache: data always comes from the server.
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // Same origin in development too: /api goes to the Spring backend.
    proxy: {
      '/api': { target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    sourcemap: false,
    // ADR-032: the ALTCHA worker stays a file served by our own domain, never a data: URL, so the strict CSP needs
    // neither data: nor blob: for workers.
    assetsInlineLimit: (file: string) => (file.includes('altcha') ? false : undefined),
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
}))
