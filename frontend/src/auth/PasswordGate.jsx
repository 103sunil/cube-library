import { Box, Button, Card, CardContent, Typography } from '@mui/material'
import { useAuth } from './AuthContext'
import { ChangePasswordForm } from '../components/ChangePassword'

/**
 * Users given a temporary password (new account, import, reset) must choose their own
 * before they can use the app.
 */
export default function PasswordGate({ children }) {
  const auth = useAuth()
  if (!auth.user?.mustChangePassword) {
    return children
  }
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', px: 2, bgcolor: '#F8FAFC' }}>
      <Card sx={{ width: '100%', maxWidth: 460, borderRadius: 5 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Typography variant="h5" component="h1">Choose your own password</Typography>
          <Typography color="text.secondary" sx={{ mt: 1, mb: 2 }}>
            Welcome{auth.user?.firstName ? `, ${auth.user.firstName}` : ''}! You signed in with a temporary password.
            Set a new one to continue.
          </Typography>
          <ChangePasswordForm submitLabel="Save and continue" onDone={auth.refresh} />
          <Button onClick={auth.logout} sx={{ mt: 1 }}>Log out</Button>
        </CardContent>
      </Card>
    </Box>
  )
}
