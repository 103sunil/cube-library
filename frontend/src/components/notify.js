import { createContext, useContext } from 'react'

export const NotifyContext = createContext(() => {})

/** notify(message, severity = 'success') shows a short toast. */
export function useNotify() {
  return useContext(NotifyContext)
}
