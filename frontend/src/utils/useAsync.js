import { useCallback, useEffect, useState } from 'react'

/**
 * Runs `loader` (memoise it with useCallback) and re-runs it when it changes or on reload().
 * While reloading, the previous data stays visible so lists don't flash.
 */
export function useAsync(loader) {
  const [reloadCount, setReloadCount] = useState(0)
  const [result, setResult] = useState({ loader: null, count: -1, data: null, error: '' })

  useEffect(() => {
    let cancelled = false
    loader().then(
      (data) => {
        if (!cancelled) setResult({ loader, count: reloadCount, data, error: '' })
      },
      (error) => {
        if (!cancelled) {
          setResult({ loader, count: reloadCount, data: null, error: error?.message || 'Something went wrong' })
        }
      }
    )
    return () => {
      cancelled = true
    }
  }, [loader, reloadCount])

  const reload = useCallback(() => setReloadCount((n) => n + 1), [])
  const loading = result.loader !== loader || result.count !== reloadCount

  return { data: result.data, error: loading ? '' : result.error, loading, reload }
}
