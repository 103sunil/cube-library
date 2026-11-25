import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'

// Flat config compatible with ESLint 8.57+ (runs on Node 18).
export default [
  { ignores: ['dist', 'node_modules', 'deploy-wars', 'frontend', 'cube-knowledge-center'] },
  {
    files: ['**/*.{js,jsx}'],
    ...js.configs.recommended,
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2021 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      ...js.configs.recommended.rules,
      // ESLint 8 core can't see JSX usage (<Box />), so ignore capitalised names (components).
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]', argsIgnorePattern: '^[A-Z_]' }],
    },
  },
  { files: ['**/*.{js,jsx}'], ...reactHooks.configs.flat.recommended },
  { files: ['**/*.{js,jsx}'], ...reactRefresh.configs.vite },
  {
    files: ['scripts/**/*.js', 'vite.config.js'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['src/test/**'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
]
