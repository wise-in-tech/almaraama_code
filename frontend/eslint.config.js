import js from '@eslint/js'
import boundaries from 'eslint-plugin-boundaries'
import reactHooks from 'eslint-plugin-react-hooks'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

const NO_HTML_INJECTION =
  'Never inject HTML (security baseline, ADR-024). Render text with JSX; screens are data rendered by the engine.'

export default defineConfig([
  globalIgnores(['dist', 'dev-dist', 'coverage', 'src/shared/api/schema.d.ts']),

  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat['recommended-latest']],
    languageOptions: { globals: globals.browser },
    rules: {
      'no-restricted-syntax': [
        'error',
        { selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']", message: NO_HTML_INJECTION },
      ],
      'no-restricted-properties': [
        'error',
        { property: 'innerHTML', message: NO_HTML_INJECTION },
        { property: 'outerHTML', message: NO_HTML_INJECTION },
        { property: 'insertAdjacentHTML', message: NO_HTML_INJECTION },
      ],
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-new-func': 'error',
      // ADR-033: every technical log goes through src/shared/log, which never receives secrets nor personal data.
      'no-console': 'error',
    },
  },

  // Module boundaries, the frontend equivalent of ArchUnit and Spring Modulith (ADR-024).
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { project: './tsconfig.app.json' } },
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'feature', pattern: 'src/features/*', capture: ['feature'] },
        { type: 'engine', pattern: 'src/engine' },
        { type: 'shared', pattern: 'src/shared' },
        { type: 'mocks', pattern: 'src/mocks' },
        { type: 'test', pattern: 'src/test' },
      ],
    },
    rules: {
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // Inside one element, files use each other freely.
            { allow: { dependency: { relationship: { to: 'internal' } } } },
            // Libraries from node_modules and Node built-ins.
            { allow: { to: { module: { origin: ['external', 'core'] } } } },
            {
              from: { element: { type: 'app' } },
              allow: { to: { element: { types: ['engine', 'shared', 'mocks'] } } },
            },
            // Features are reached only through their public index.ts.
            {
              from: { element: { types: ['app', 'feature'] } },
              allow: { to: { element: { type: 'feature', internalPath: 'index.ts' } } },
            },
            {
              from: { element: { type: 'feature' } },
              allow: { to: { element: { types: ['engine', 'shared'] } } },
            },
            { from: { element: { type: 'engine' } }, allow: { to: { element: { type: 'shared' } } } },
            { from: { element: { type: 'mocks' } }, allow: { to: { element: { type: 'shared' } } } },
            { from: { element: { type: 'test' } }, allow: { to: { element: { types: ['engine', 'shared'] } } } },
          ],
        },
      ],
    },
  },
])
