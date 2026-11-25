import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Chip,
  Container,
  Divider,
  ListItemIcon,
  Menu,
  MenuItem,
  Toolbar,
  Typography,
} from '@mui/material'
import LogoutIcon from '@mui/icons-material/Logout'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import ExploreOutlinedIcon from '@mui/icons-material/ExploreOutlined'
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutlineOutlined'
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined'
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined'
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined'
import { useAuth } from '../auth/AuthContext'
import { ChangePasswordDialog } from './ChangePassword'
import { useNotify } from './notify'
import { ADMIN_PERMS, PERM } from '../config'
import { BRAND } from '../theme'

const NAV = [
  { to: '/', label: 'Discover', permission: PERM.VIEW, end: true, Icon: ExploreOutlinedIcon },
  { to: '/submit', label: 'Submit', permission: PERM.SUBMIT, Icon: AddCircleOutlineIcon },
  { to: '/mine', label: 'My uploads', permission: PERM.SUBMIT, Icon: FolderOutlinedIcon },
  { to: '/bookmarks', label: 'Bookmarks', permission: PERM.VIEW, Icon: BookmarkBorderIcon },
  { to: '/approvals', label: 'Approvals', permission: PERM.APPROVE, Icon: FactCheckOutlinedIcon },
  { to: '/team', label: 'My team', permission: PERM.APPROVE, Icon: GroupsOutlinedIcon },
  { to: '/admin', label: 'Admin', anyOf: ADMIN_PERMS, Icon: AdminPanelSettingsOutlinedIcon },
]

function initials(name) {
  return (
    String(name || '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('') || 'U'
  )
}

function roleLabel(groupCode) {
  if (!groupCode) return 'User'
  return groupCode.charAt(0) + groupCode.slice(1).toLowerCase()
}

export default function AppLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuAnchor, setMenuAnchor] = useState(null)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const notify = useNotify()
  const displayName = auth.user?.displayName || auth.user?.username || 'User'
  const items = NAV.filter((item) => (item.anyOf ? item.anyOf.some((p) => auth.can(p)) : auth.can(item.permission)))

  const handleLogout = async () => {
    setMenuAnchor(null)
    await auth.logout()
    navigate('/login', { replace: true })
  }

  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar
        position="sticky"
        color="inherit"
        elevation={0}
        sx={{
          bgcolor: 'rgba(255,255,255,0.78)',
          backdropFilter: 'saturate(180%) blur(14px)',
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Toolbar sx={{ gap: 3, minHeight: { xs: 64, md: 70 }, px: { xs: 2, md: 4 } }}>
          <Box
            component={NavLink}
            to="/"
            sx={{ display: 'flex', alignItems: 'center', gap: 1.25, textDecoration: 'none', color: 'inherit', flexShrink: 0 }}
          >
            <Box
              sx={{
                width: 42,
                height: 42,
                borderRadius: 3.5,
                display: 'grid',
                placeItems: 'center',
                bgcolor: '#FFFFFF',
                boxShadow: '0 8px 20px -10px rgba(79,70,229,0.6), inset 0 0 0 1px rgba(79,70,229,0.12)',
                transition: 'transform .25s ease',
                '&:hover': { transform: 'rotate(-6deg) scale(1.05)' },
              }}
            >
              <Box component="img" src={`${import.meta.env.BASE_URL}logo.png`} alt="" sx={{ width: 32, height: 32, objectFit: 'contain' }} />
            </Box>
            <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
              <Typography
                sx={{
                  fontFamily: '"Plus Jakarta Sans", Inter, sans-serif',
                  fontWeight: 800,
                  lineHeight: 1.1,
                  letterSpacing: '0.06em',
                  background: BRAND.gradient,
                  WebkitBackgroundClip: 'text',
                  backgroundClip: 'text',
                  color: 'transparent',
                }}
              >
                CUBE
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Knowledge Repository
              </Typography>
            </Box>
          </Box>

          <Box
            component="nav"
            aria-label="Main"
            sx={{
              display: 'flex',
              gap: 0.5,
              flexGrow: 1,
              overflowX: 'auto',
              p: 0.5,
              borderRadius: 999,
              bgcolor: 'rgba(238,240,247,0.85)',
              border: '1px solid',
              borderColor: 'divider',
              maxWidth: 'fit-content',
              '&::-webkit-scrollbar': { display: 'none' },
            }}
          >
            {items.map(({ to, label, end, Icon }) => (
              <Button
                key={to}
                component={NavLink}
                to={to}
                end={end}
                startIcon={<Icon sx={{ fontSize: '18px !important' }} />}
                sx={{
                  color: 'text.secondary',
                  px: 1.75,
                  py: 0.75,
                  borderRadius: 999,
                  whiteSpace: 'nowrap',
                  fontSize: 14,
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.9)', color: 'text.primary' },
                  transition: 'color .2s ease, background-color .2s ease, box-shadow .2s ease',
                  '&.active': {
                    color: 'primary.dark',
                    bgcolor: '#FFFFFF',
                    boxShadow: '0 1px 2px rgba(15,23,42,0.08), 0 6px 14px -8px rgba(79,70,229,0.55)',
                  },
                  '&.active .MuiButton-startIcon': { color: 'primary.main' },
                }}
              >
                {label}
              </Button>
            ))}
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <Button
            onClick={(e) => setMenuAnchor(e.currentTarget)}
            aria-label="Account menu"
            endIcon={<KeyboardArrowDownIcon sx={{ display: { xs: 'none', md: 'block' } }} />}
            sx={{ color: 'text.primary', borderRadius: 999, pl: 0.75, pr: { xs: 0.75, md: 1.5 }, flexShrink: 0 }}
          >
            <Avatar
              sx={{
                width: 36,
                height: 36,
                fontSize: 14,
                fontWeight: 700,
                background: BRAND.gradient,
                boxShadow: '0 0 0 2px #FFFFFF, 0 0 0 4px rgba(79,70,229,0.25)',
              }}
            >
              {initials(displayName)}
            </Avatar>
            <Box sx={{ textAlign: 'left', ml: 1.25, display: { xs: 'none', md: 'block' } }}>
              <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
                {displayName}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {roleLabel(auth.groupCode)}
              </Typography>
            </Box>
          </Button>
          <Menu
            anchorEl={menuAnchor}
            open={Boolean(menuAnchor)}
            onClose={() => setMenuAnchor(null)}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            slotProps={{ paper: { sx: { mt: 1, minWidth: 270, overflow: 'hidden' } } }}
          >
            <Box sx={{ px: 2, py: 2, mx: 0.75, mb: 0.75, borderRadius: 2.5, background: 'linear-gradient(135deg, #EEF2FF, #F5F3FF)', display: 'flex', gap: 1.5, alignItems: 'center' }}>
              <Avatar sx={{ width: 44, height: 44, background: BRAND.gradient }}>{initials(displayName)}</Avatar>
              <Box sx={{ minWidth: 0 }}>
                <Typography sx={{ fontWeight: 700 }}>{displayName}</Typography>
                <Typography variant="body2" color="text.secondary" noWrap>
                  {auth.user?.email}
                </Typography>
                <Box sx={{ display: 'flex', gap: 0.75, mt: 0.75, flexWrap: 'wrap' }}>
                  <Chip size="small" label={roleLabel(auth.groupCode)} color="primary" sx={{ height: 22 }} />
                  {auth.user?.designation && <Chip size="small" variant="outlined" label={auth.user.designation} sx={{ height: 22 }} />}
                </Box>
              </Box>
            </Box>
            <Divider />
            <MenuItem
              onClick={() => {
                setMenuAnchor(null)
                setPasswordOpen(true)
              }}
              sx={{ py: 1.25 }}
            >
              <ListItemIcon>
                <KeyOutlinedIcon fontSize="small" />
              </ListItemIcon>
              Change password
            </MenuItem>
            <MenuItem onClick={handleLogout} sx={{ py: 1.25 }}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Log out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 }, minHeight: 'calc(100vh - 150px)' }}>
        <Box key={location.pathname} sx={{ animation: 'cubeFadeUp .35s ease both' }}>
          <Outlet />
        </Box>
      </Container>
      <Box component="footer" sx={{ py: 3, textAlign: 'center', color: 'text.secondary', fontSize: 13 }}>
        CUBE Knowledge Repository · {new Date().getFullYear()}
      </Box>
      {passwordOpen && (
        <ChangePasswordDialog
          onClose={() => setPasswordOpen(false)}
          onChanged={() => {
            setPasswordOpen(false)
            notify('Password changed')
          }}
        />
      )}
    </Box>
  )
}
