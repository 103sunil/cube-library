import { createContext, useContext } from 'react'

export const AuthContext = createContext(null)

/**
 * { status: 'loading' | 'authenticated' | 'anonymous', user, permissions, groupCode,
 *   expired, login(username, password), logout(), can(permission), refresh() }
 */
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
