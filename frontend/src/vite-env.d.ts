/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" with `pnpm dev:mocks`: endpoints not implemented yet are answered from mocks of the contract. */
  readonly VITE_API_MOCKS?: string
  /** Technical log level (ADR-033): `silent`, `warn`, `info` or `debug`. */
  readonly VITE_LOG_LEVEL?: string
}
