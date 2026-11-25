import { useCallback, useState } from 'react'
import { Alert, Snackbar } from '@mui/material'
import { NotifyContext } from './notify'

export default function NotifyProvider({ children }) {
  const [toast, setToast] = useState(null)

  const notify = useCallback((message, severity = 'success') => {
    setToast({ message, severity, key: Date.now() })
  }, [])

  return (
    <NotifyContext.Provider value={notify}>
      {children}
      <Snackbar
        key={toast?.key}
        open={Boolean(toast)}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {toast ? (
          <Alert severity={toast.severity} variant="filled" onClose={() => setToast(null)} sx={{ width: '100%' }}>
            {toast.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotifyContext.Provider>
  )
}
