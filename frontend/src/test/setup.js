import { afterEach, vi } from 'vitest'
import { cleanup, configure } from '@testing-library/react'

// The first render in a file is slow when all files run in parallel; allow 4s for findBy/waitFor.
configure({ asyncUtilTimeout: 4000 })

if (!URL.createObjectURL) URL.createObjectURL = () => 'blob:mock'
if (!URL.revokeObjectURL) URL.revokeObjectURL = () => {}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
