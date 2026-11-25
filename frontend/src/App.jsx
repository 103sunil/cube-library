import { Link as RouterLink, Navigate, Route, Routes } from 'react-router-dom'
import { Box, Button, Typography } from '@mui/material'
import RequireAuth from './auth/RequireAuth'
import PasswordGate from './auth/PasswordGate'
import { useAuth } from './auth/AuthContext'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import DiscoverPage from './pages/DiscoverPage'
import KnowledgeDetailPage from './pages/KnowledgeDetailPage'
import { EditPage, SubmitPage } from './pages/KnowledgeFormPage'
import MyUploadsPage from './pages/MyUploadsPage'
import ApprovalsPage from './pages/ApprovalsPage'
import BookmarksPage from './pages/BookmarksPage'
import TeamPage from './pages/TeamPage'
import AdminPage from './pages/admin/AdminPage'
import { ADMIN_PERMS, PERM } from './config'

/** Landing page: Discover for readers, otherwise the first page the user may use. */
function HomeRoute() {
  const auth = useAuth()
  if (auth.can(PERM.VIEW)) return <DiscoverPage />
  if (auth.can(PERM.APPROVE)) return <Navigate to="/approvals" replace />
  if (auth.can(PERM.SUBMIT)) return <Navigate to="/mine" replace />
  if (ADMIN_PERMS.some((p) => auth.can(p))) return <Navigate to="/admin" replace />
  return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <Typography variant="h5">No access yet</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>
        Your account has no permissions. Ask an administrator to add you to a group.
      </Typography>
    </Box>
  )
}

function NotFound() {
  return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <Typography variant="h4">Page not found</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>
        The page you are looking for doesn&apos;t exist.
      </Typography>
      <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 3 }}>
        Go to Discover
      </Button>
    </Box>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <PasswordGate>
              <AppLayout />
            </PasswordGate>
          </RequireAuth>
        }
      >
        <Route index element={<HomeRoute />} />
        <Route path="submit" element={<RequireAuth permission={PERM.SUBMIT}><SubmitPage /></RequireAuth>} />
        <Route path="mine" element={<RequireAuth permission={PERM.SUBMIT}><MyUploadsPage /></RequireAuth>} />
        <Route path="knowledge/:id" element={<KnowledgeDetailPage />} />
        <Route path="knowledge/:id/edit" element={<EditPage />} />
        <Route path="bookmarks" element={<RequireAuth permission={PERM.VIEW}><BookmarksPage /></RequireAuth>} />
        <Route path="team" element={<RequireAuth permission={PERM.APPROVE}><TeamPage /></RequireAuth>} />
        <Route path="approvals" element={<RequireAuth permission={PERM.APPROVE}><ApprovalsPage /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth anyOf={ADMIN_PERMS}><AdminPage /></RequireAuth>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
