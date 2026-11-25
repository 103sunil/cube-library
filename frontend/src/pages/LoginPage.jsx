import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Box, Button, CircularProgress, IconButton, InputAdornment, TextField, Typography } from '@mui/material'
import Visibility from '@mui/icons-material/Visibility'
import VisibilityOff from '@mui/icons-material/VisibilityOff'
import ManageSearchIcon from '@mui/icons-material/ManageSearch'
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import PersonOutlineIcon from '@mui/icons-material/PersonOutlined'
import { useAuth } from '../auth/AuthContext'
import { BRAND } from '../theme'

const FEATURES = [
  { Icon: ManageSearchIcon, title: 'Find answers fast', text: 'Search every guide, fix and document in one place.' },
  { Icon: VerifiedOutlinedIcon, title: 'Reviewed by your manager', text: 'Uploads are approved before everyone sees them.' },
  { Icon: LockOutlinedIcon, title: 'Secure by design', text: 'Access follows your role and your team.' },
]

function BrandPanel() {
  return (
    <Box
      sx={{
        display: { xs: 'none', md: 'flex' },
        flexDirection: 'column',
        justifyContent: 'space-between',
        position: 'relative',
        overflow: 'hidden',
        color: '#FFFFFF',
        p: 6,
        background: BRAND.heroGradient,
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          inset: 0,
          opacity: 0.16,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.4) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.4) 1px, transparent 1px)',
          backgroundSize: '48px 48px',
          maskImage: 'radial-gradient(ellipse at 30% 40%, black 10%, transparent 70%)',
          WebkitMaskImage: 'radial-gradient(ellipse at 30% 40%, black 10%, transparent 70%)',
        }}
      />
      <Box sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Box sx={{ width: 48, height: 48, borderRadius: 3.5, bgcolor: '#FFFFFF', display: 'grid', placeItems: 'center', boxShadow: '0 12px 30px -12px rgba(0,0,0,0.5)' }}>
          <Box component="img" src={`${import.meta.env.BASE_URL}logo.png`} alt="" sx={{ width: 36, height: 36, objectFit: 'contain' }} />
        </Box>
        <Box>
          <Typography sx={{ fontWeight: 800, letterSpacing: '0.08em', fontFamily: '"Plus Jakarta Sans", Inter, sans-serif' }}>CUBE</Typography>
          <Typography variant="body2" sx={{ opacity: 0.75 }}>Knowledge Repository</Typography>
        </Box>
      </Box>

      <Box sx={{ position: 'relative', maxWidth: 480 }}>
        <Typography variant="h3" component="p" sx={{ color: '#FFFFFF', fontSize: 40, lineHeight: 1.15 }}>
          Everything your team knows, in one place.
        </Typography>
        <Box sx={{ mt: 4, display: 'grid', gap: 2.25 }}>
          {FEATURES.map(({ Icon, title, text }) => (
            <Box key={title} sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
              <Box sx={{ width: 40, height: 40, flexShrink: 0, borderRadius: 2.5, display: 'grid', placeItems: 'center', bgcolor: 'rgba(255,255,255,0.14)', border: '1px solid rgba(255,255,255,0.22)' }}>
                <Icon sx={{ fontSize: 21 }} />
              </Box>
              <Box>
                <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
                <Typography variant="body2" sx={{ opacity: 0.75 }}>{text}</Typography>
              </Box>
            </Box>
          ))}
        </Box>
      </Box>

      <Typography variant="body2" sx={{ position: 'relative', opacity: 0.6 }}>
        © {new Date().getFullYear()} CUBE · Internal use only
      </Typography>
    </Box>
  )
}

export default function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // Always start on the home page for the signed-in role. Only a direct link to a knowledge
  // item (e.g. shared by a colleague) is reopened after login; any other remembered page may
  // belong to a different user/role and would land people on a random tab.
  const fromPath = location.state?.from?.pathname || ''
  const target = /^\/knowledge\/\d+$/.test(fromPath) ? fromPath : '/'

  if (auth.status === 'authenticated') {
    return <Navigate to={target} replace />
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const errors = {}
    if (!username.trim()) errors.username = 'Enter your username'
    if (!password) errors.password = 'Enter your password'
    setFieldErrors(errors)
    if (Object.keys(errors).length) return

    setBusy(true)
    setError('')
    try {
      await auth.login(username.trim(), password)
      navigate(target, { replace: true })
    } catch (err) {
      setError(err.message || 'Login failed. Please try again.')
      setBusy(false)
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.05fr 1fr' }, bgcolor: '#FFFFFF' }}>
      <BrandPanel />

      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', px: { xs: 3, sm: 6 }, py: 6, position: 'relative' }}>
        <Box sx={{ width: '100%', maxWidth: 400, animation: 'cubeFadeUp .45s ease both' }}>
          {/* Logo for small screens, where the brand panel is hidden */}
          <Box sx={{ display: { xs: 'flex', md: 'none' }, justifyContent: 'center', mb: 3 }}>
            <Box sx={{ width: 68, height: 68, borderRadius: 4.5, display: 'grid', placeItems: 'center', bgcolor: '#FFFFFF', boxShadow: '0 14px 34px -14px rgba(79,70,229,0.55)' }}>
              <Box component="img" src={`${import.meta.env.BASE_URL}logo.png`} alt="" sx={{ width: 50, height: 50, objectFit: 'contain' }} />
            </Box>
          </Box>

          <Typography variant="h4" component="h1" sx={{ fontSize: { xs: 28, md: 32 } }}>
            Sign in to CUBE
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1, mb: 4 }}>
            Welcome back. Use your company username and password.
          </Typography>

          {auth.expired && !error && (
            <Alert severity="info" sx={{ mb: 2.5 }}>
              Your session has ended. Please sign in again.
            </Alert>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 2.5 }}>
              {error}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <TextField
              fullWidth
              label="Username"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              error={Boolean(fieldErrors.username)}
              helperText={fieldErrors.username}
              sx={{ mb: 2.25 }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <PersonOutlineIcon sx={{ color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                },
              }}
            />
            <TextField
              fullWidth
              label="Password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={Boolean(fieldErrors.password)}
              helperText={fieldErrors.password}
              sx={{ mb: 3.5 }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <LockOutlinedIcon sx={{ color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                        onClick={() => setShowPassword((v) => !v)}
                        edge="end"
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />
            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={busy}
              startIcon={busy ? <CircularProgress size={18} color="inherit" /> : null}
              sx={{ py: 1.5, fontSize: 16 }}
            >
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 3.5, textAlign: 'center' }}>
            Forgot your password? Ask your manager or administrator to reset it.
          </Typography>
        </Box>
      </Box>
    </Box>
  )
}
