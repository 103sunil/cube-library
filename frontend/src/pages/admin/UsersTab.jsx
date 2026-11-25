import { useCallback, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined'
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'
import { adminApi } from '../../api/cube'
import { useAsync } from '../../utils/useAsync'
import { formatDate } from '../../utils/format'
import { MODULE_LABELS, PERM, canReportTo, designationLevel, permissionLabel } from '../../config'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/common'
import { EmployeeFields } from '../../components/EmployeeFields'
import ImportUsersDialog from './ImportUsersDialog'
import DeleteUserDialog from '../../components/DeleteUserDialog'
import { useNotify } from '../../components/notify'

const labelFor = (code) => permissionLabel(code)

const MANAGERIAL_ROLE = /manager|vice president|\bvp\b|director|head/i
const ACCESS_LABELS = {
  EMPLOYEE: 'Employee – uploads and reads knowledge',
  MANAGER: 'Manager / VP – approves uploads, manages a team',
  ADMIN: 'Administrator – manages users only',
}
const ACCESS_HELP = {
  EMPLOYEE: 'Cannot approve or have a team',
  MANAGER: 'Sees Approvals and My team; can add employees',
  ADMIN: 'Sees only the Admin page',
}

function UserDialog({ user, groups, approvers, onClose, onSaved }) {
  const notify = useNotify()
  const creating = !user
  const [form, setForm] = useState({
    username: '',
    password: '',
    email: user?.email || '',
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    groupCode: user?.groupCode || groups.find((g) => g.groupCode === 'EMPLOYEE')?.groupCode || groups[0]?.groupCode || '',
    status: user?.status || 'A',
    reportingManagerUsername: user?.reportingManagerUsername || '',
    employeeCode: user?.employeeCode || '',
    designation: user?.designation || '',
  })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const hasManagerGroup = groups.some((g) => g.groupCode === 'MANAGER' && g.status === 'A')
  // Picking a managerial role (Manager, VP, ...) also gives manager access, so the two can't disagree.
  const setFormWithAccess = (update) =>
    setForm((f) => {
      const next = typeof update === 'function' ? update(f) : update
      if (
        hasManagerGroup &&
        next.designation !== f.designation &&
        next.groupCode === 'EMPLOYEE' &&
        MANAGERIAL_ROLE.test(next.designation || '')
      ) {
        return { ...next, groupCode: 'MANAGER' }
      }
      return next
    })

  // Only Team Lead or higher, never below this person's level, never themselves.
  const personLevel = designationLevel(form.designation, form.groupCode)
  const allowedManagers = approvers.filter((a) => a.username !== user?.username && canReportTo(a, personLevel))
  const reportsToOk = !form.reportingManagerUsername || allowedManagers.some((a) => a.username === form.reportingManagerUsername)

  const save = async () => {
    if (!reportsToOk) {
      setError(`Choose a new "Reports to": ${form.reportingManagerUsername} is below this person's level.`)
      return
    }
    setBusy(true)
    setError('')
    setFieldErrors({})
    try {
      const body = creating
        ? form
        : {
            email: form.email,
            firstName: form.firstName,
            lastName: form.lastName,
            groupCode: form.groupCode,
            status: form.status,
            reportingManagerUsername: form.reportingManagerUsername,
            employeeCode: form.employeeCode.trim(),
            designation: form.designation.trim(),
          }
      await (creating ? adminApi.createUser(body) : adminApi.updateUser(user.userId, body))
      notify(creating ? `User ${form.username} created` : 'User updated')
      onSaved()
    } catch (err) {
      setError(err.message)
      setFieldErrors(err.fieldErrors || {})
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{creating ? 'New user' : `Edit ${user.username}`}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
          {creating && (
            <TextField label="Username" required value={form.username} onChange={set('username')} error={Boolean(fieldErrors.username)} helperText={fieldErrors.username} />
          )}
          {creating && (
            <TextField
              label="Initial password"
              type="password"
              required
              value={form.password}
              onChange={set('password')}
              error={Boolean(fieldErrors.password)}
              helperText={fieldErrors.password || 'At least 8 characters'}
            />
          )}
          <TextField label="First name" value={form.firstName} onChange={set('firstName')} />
          <TextField label="Last name" value={form.lastName} onChange={set('lastName')} />
          <TextField
            label="Email"
            required
            value={form.email}
            onChange={set('email')}
            error={Boolean(fieldErrors.email)}
            helperText={fieldErrors.email}
            sx={{ gridColumn: { sm: '1 / -1' } }}
          />
          <EmployeeFields form={form} setForm={setFormWithAccess} fieldErrors={fieldErrors} />
          <TextField
            select
            label="Access (what they can do)"
            value={form.groupCode}
            onChange={set('groupCode')}
            helperText={ACCESS_HELP[form.groupCode] || ''}
          >
            {groups.map((g) => (
              <MenuItem key={g.groupId} value={g.groupCode} disabled={g.status !== 'A'}>
                {ACCESS_LABELS[g.groupCode] || g.groupName}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Status" value={form.status} onChange={set('status')}>
            <MenuItem value="A">Active</MenuItem>
            <MenuItem value="I">Inactive (cannot sign in)</MenuItem>
          </TextField>
          <TextField
            select
            label="Reports to"
            value={reportsToOk ? form.reportingManagerUsername : ''}
            onChange={set('reportingManagerUsername')}
            helperText={
              !reportsToOk
                ? `${form.reportingManagerUsername} is below their level now. Choose again.`
                : form.reportingManagerUsername
                ? 'Team Lead or higher, at least their own level. Uploads go to the nearest manager above them.'
                : form.groupCode === 'EMPLOYEE'
                  ? 'Without a manager, any manager can approve their uploads. Usually pick their manager.'
                  : 'Top of the hierarchy (e.g. Senior VP): reports to no one, and their own uploads publish directly'
            }
            sx={{ gridColumn: { sm: '1 / -1' } }}
          >
            <MenuItem value="">No one (top of hierarchy)</MenuItem>
            {allowedManagers.map((a) => (
              <MenuItem key={a.userId} value={a.username}>
                {a.displayName} ({a.username}){a.designation ? ` · ${a.designation}` : ''}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy}>
          {creating ? 'Create user' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function PasswordDialog({ user, onClose }) {
  const notify = useNotify()
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }
    setBusy(true)
    try {
      await adminApi.resetPassword(user.userId, password)
      notify(`Password reset for ${user.username}`)
      onClose()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Reset password for {user.username}</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          fullWidth
          type="password"
          label="New password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={Boolean(error)}
          helperText={error || 'At least 8 characters. Share it with the user securely.'}
          sx={{ mt: 1 }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy}>Reset password</Button>
      </DialogActions>
    </Dialog>
  )
}

function PermissionsDialog({ user, onClose }) {
  const notify = useNotify()
  const loader = useCallback(
    () =>
      Promise.all([adminApi.userPermissions(user.userId), adminApi.overrides(user.userId), adminApi.accessCodes()]),
    [user.userId]
  )
  const { data, loading, error, reload } = useAsync(loader)
  const [form, setForm] = useState({ code: PERM.APPROVE, allowed: 'true', expiryDate: '' })
  const [busy, setBusy] = useState(false)
  const options = (data?.[2] || []).filter((a) => a.status !== 'I')

  const add = async () => {
    const option = options.find((p) => p.permission === form.code)
    if (!option) return
    setBusy(true)
    try {
      await adminApi.upsertOverride(user.userId, {
        moduleCode: option.moduleCode,
        accessCode: option.code,
        allowed: form.allowed === 'true',
        expiryDate: form.expiryDate || null,
      })
      notify('Override saved')
      reload()
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (override) => {
    setBusy(true)
    try {
      await adminApi.deleteOverride(user.userId, override.moduleCode, override.accessCode)
      notify('Override removed')
      reload()
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const [effective, overrides] = data || [[], []]

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Permissions for {user.username}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        {loading && !data && <CircularProgress aria-label="Loading" />}
        {data && (
          <>
            <Typography sx={{ fontWeight: 600, mb: 1 }}>Effective permissions</Typography>
            <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mb: 3 }}>
              {effective.length === 0 && <Typography color="text.secondary">None</Typography>}
              {effective.map((code) => (
                <Chip key={code} label={labelFor(code)} color="primary" variant="outlined" size="small" />
              ))}
            </Box>

            <Typography sx={{ fontWeight: 600 }}>Personal overrides</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Grant or remove a permission for this user only, on top of their group. Optional expiry date (inclusive).
            </Typography>
            {overrides.length > 0 && (
              <Table size="small" sx={{ mb: 2 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Permission</TableCell>
                    <TableCell>Effect</TableCell>
                    <TableCell>Expires</TableCell>
                    <TableCell>State</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {overrides.map((o) => (
                    <TableRow key={o.permission}>
                      <TableCell>{labelFor(o.permission)}</TableCell>
                      <TableCell>{o.allowed ? 'Grant' : 'Remove'}</TableCell>
                      <TableCell>{o.expiryDate ? formatDate(o.expiryDate) : 'Never'}</TableCell>
                      <TableCell>{o.effective ? 'Active' : 'Expired'}</TableCell>
                      <TableCell align="right">
                        <IconButton aria-label={`Remove override ${o.permission}`} disabled={busy} onClick={() => remove(o)}>
                          <DeleteOutlineIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
              <TextField select size="small" label="Permission" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} sx={{ minWidth: 180 }}>
                {options.map((p) => (
                  <MenuItem key={p.permission} value={p.permission}>
                    {(MODULE_LABELS[p.moduleCode] || p.moduleCode) + ': ' + permissionLabel(p.permission, p.code)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField select size="small" label="Effect" value={form.allowed} onChange={(e) => setForm({ ...form, allowed: e.target.value })} sx={{ minWidth: 120 }}>
                <MenuItem value="true">Grant</MenuItem>
                <MenuItem value="false">Remove</MenuItem>
              </TextField>
              <TextField
                size="small"
                type="date"
                label="Expires on"
                value={form.expiryDate}
                onChange={(e) => setForm({ ...form, expiryDate: e.target.value })}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Button variant="contained" onClick={add} disabled={busy}>Add override</Button>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}

export default function UsersTab() {
  const auth = useAuth()
  const notify = useNotify()
  const canOverride = auth.can(PERM.MANAGE_USER_ACCESS)
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0, removed: false })
  const [dialog, setDialog] = useState(null)

  const loader = useCallback(() => Promise.all([adminApi.users(search.q, search.page, 20, search.removed), adminApi.groups(), adminApi.reportingOptions()]), [search])
  const { data, loading, error, reload } = useAsync(loader)
  const [users, groups, approvers] = data || [null, [], []]

  const closeAndReload = () => {
    setDialog(null)
    reload()
  }

  const restore = async (u) => {
    try {
      await adminApi.restoreUser(u.userId)
      notify(`${u.username} restored. Set their manager in Edit if needed.`)
      reload()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  return (
    <Box>
      {search.removed && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Deleted users cannot sign in. Their published knowledge is still available. Restore brings an account back.
        </Alert>
      )}
      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault()
          setSearch({ ...search, q: query.trim(), page: 0 })
        }}
        sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}
      >
        <TextField size="small" placeholder="Search username or email" value={query} onChange={(e) => setQuery(e.target.value)} sx={{ flex: '1 1 260px' }} />
        <Button type="submit" variant="outlined">Search</Button>
        <Button onClick={() => setSearch({ ...search, page: 0, removed: !search.removed })}>
          {search.removed ? 'Back to current users' : 'Deleted users'}
        </Button>
        <Button variant="outlined" onClick={() => setDialog({ type: 'import' })} disabled={!data}>
          Import from CSV
        </Button>
        <Button variant="contained" onClick={() => setDialog({ type: 'user' })} disabled={!data}>
          New user
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CircularProgress aria-label="Loading" />}
      {users && (
        <TableContainer component={Paper} variant="outlined" sx={{ bgcolor: '#FFFFFF' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>User</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Access</TableCell>
                <TableCell>Reports to</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.content.map((u) => (
                <TableRow key={u.userId} hover>
                  <TableCell>
                    <Typography sx={{ fontWeight: 600 }}>{u.username}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {[u.firstName, u.lastName].filter(Boolean).join(' ')}
                    </Typography>
                    {(u.employeeCode || u.designation) && (
                      <Typography variant="caption" color="text.secondary">
                        {[u.employeeCode, u.designation].filter(Boolean).join(' · ')}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    {u.groupName || '—'}
                    {u.groupCode === 'EMPLOYEE' && MANAGERIAL_ROLE.test(u.designation || '') && (
                      <Typography variant="caption" color="warning.main" sx={{ display: 'block' }}>
                        Role is {u.designation} but access is Employee. Edit to give manager access.
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{u.reportingManagerName || (u.groupCode === 'MANAGER' ? 'No one (top)' : '—')}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={u.status === 'A' ? 'Active' : 'Inactive'}
                      color={u.status === 'A' ? 'success' : 'default'}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {search.removed ? (
                      <Button size="small" onClick={() => restore(u)}>Restore</Button>
                    ) : (
                    <>
                    <Tooltip title="Edit">
                      <IconButton aria-label={`Edit ${u.username}`} onClick={() => setDialog({ type: 'user', user: u })}>
                        <EditOutlinedIcon />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Reset password">
                      <IconButton aria-label={`Reset password for ${u.username}`} onClick={() => setDialog({ type: 'password', user: u })}>
                        <KeyOutlinedIcon />
                      </IconButton>
                    </Tooltip>
                    {canOverride && (
                      <Tooltip title="Permissions">
                        <IconButton aria-label={`Permissions for ${u.username}`} onClick={() => setDialog({ type: 'permissions', user: u })}>
                          <AdminPanelSettingsOutlinedIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                    {u.username !== auth.user?.username && (
                      <Tooltip title="Delete (left the company)">
                        <IconButton aria-label={`Delete ${u.username}`} color="error" onClick={() => setDialog({ type: 'delete', user: u })}>
                          <DeleteOutlineIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                    </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {users.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">{search.removed ? 'No deleted users' : 'No users found'}</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {users && <Pager page={users.page} totalPages={users.totalPages} onChange={(p) => setSearch({ ...search, page: p })} />}

      {dialog?.type === 'user' && <UserDialog user={dialog.user} groups={groups} approvers={approvers} onClose={() => setDialog(null)} onSaved={closeAndReload} />}
      {dialog?.type === 'password' && <PasswordDialog user={dialog.user} onClose={() => setDialog(null)} />}
      {dialog?.type === 'import' && <ImportUsersDialog onClose={closeAndReload} />}
      {dialog?.type === 'delete' && (
        <DeleteUserDialog
          person={dialog.user}
          remove={() => adminApi.removeUser(dialog.user.userId)}
          onClose={() => setDialog(null)}
          onDeleted={closeAndReload}
        />
      )}
      {dialog?.type === 'permissions' && <PermissionsDialog user={dialog.user} onClose={closeAndReload} />}
    </Box>
  )
}
