import { Navigate, useLocation } from 'react-router-dom'
import { Box, CircularProgress, Paper, Typography } from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { useAuth } from './AuthContext'

/**
 * Guards a route. Unauthenticated users go to /login (and come back afterwards).
 * `permission` (one code) or `anyOf` (list) restrict the route further.
 */
export default function RequireAuth({ permission, anyOf, children }) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress aria-label="Loading" />
      </Box>
    )
  }
  if (auth.status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  const allowed = permission ? auth.can(permission) : anyOf ? anyOf.some((p) => auth.can(p)) : true
  if (!allowed) {
    return (
      <Paper variant="outlined" sx={{ p: 6, textAlign: 'center', maxWidth: 560, mx: 'auto', mt: 6 }}>
        <LockOutlinedIcon color="disabled" sx={{ fontSize: 48 }} />
        <Typography variant="h6" sx={{ mt: 1 }}>
          You don&apos;t have access to this page
        </Typography>
        <Typography color="text.secondary">Ask an administrator if you think you should.</Typography>
      </Paper>
    )
  }
  return children
}
