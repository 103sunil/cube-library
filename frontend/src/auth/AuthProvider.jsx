import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './AuthContext'
import { authApi } from '../api/cube'
import { setUnauthorizedHandler, tokenStore } from '../api/client'

const ANONYMOUS = { status: 'anonymous', user: null, permissions: [], groupCode: null, expired: false }

/** Reads the signed-in user's profile and permissions from the backend. */
async function fetchSession() {
  const [user, perms] = await Promise.all([authApi.me(), authApi.permissions()])
  return {
    status: 'authenticated',
    user,
    permissions: perms?.permissions || [],
    groupCode: perms?.groupCode || user?.groupCode || null,
    expired: false,
  }
}

export default function AuthProvider({ children }) {
  const [state, setState] = useState(() =>
    tokenStore.get() ? { ...ANONYMOUS, status: 'loading' } : ANONYMOUS
  )

  const loadSession = useCallback(async () => {
    setState(await fetchSession())
  }, [])

  // Restore a session from a stored token on first load.
  useEffect(() => {
    if (!tokenStore.get()) return
    fetchSession().then(setState, () => {
      tokenStore.clear()
      setState(ANONYMOUS)
    })
  }, [])

  // Any 401 from the API ends the session and sends the user back to the login page.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      tokenStore.clear()
      setState({ ...ANONYMOUS, expired: true })
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const login = useCallback(
    async (username, password) => {
      const response = await authApi.login(username, password)
      tokenStore.set(response.token)
      try {
        await loadSession()
      } catch (error) {
        tokenStore.clear()
        throw error
      }
    },
    [loadSession]
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // The token is cleared locally either way.
    } finally {
      tokenStore.clear()
      setState(ANONYMOUS)
    }
  }, [])

  const can = useCallback((permission) => state.permissions.includes(permission), [state.permissions])

  const value = useMemo(
    () => ({ ...state, login, logout, can, refresh: loadSession }),
    [state, login, logout, can, loadSession]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
