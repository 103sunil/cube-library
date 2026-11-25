import TeamIcon from '@mui/icons-material/GroupsOutlined'
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
  ListSubheader,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { teamApi } from '../api/cube'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../utils/useAsync'
import { EmptyState, PageHeader } from '../components/common'
import { EmployeeFields } from '../components/EmployeeFields'
import DeleteUserDialog from '../components/DeleteUserDialog'
import { useNotify } from '../components/notify'
import { LEVEL, LEVEL_NAMES, canReportTo, designationLevel } from '../config'

const optionLabel = (m, me) =>
  `${m.displayName} (${m.username})${m.designation ? ` · ${m.designation}` : ''}${m.username === me ? ' – me' : ''}`

/** Usernames of everyone below `username` in the team list. */
function peopleBelow(username, members) {
  const below = new Set()
  let frontier = [username]
  while (frontier.length) {
    const next = members.filter((m) => frontier.includes(m.reportingManagerUsername) && !below.has(m.username))
    next.forEach((m) => below.add(m.username))
    frontier = next.map((m) => m.username)
  }
  return below
}

function MemberDialog({ member, members, managers, me, onClose, onSaved }) {
  const notify = useNotify()
  const creating = !member
  const [form, setForm] = useState({
    username: '',
    password: '',
    email: member?.email || '',
    firstName: member?.firstName || '',
    lastName: member?.lastName || '',
    status: member?.status || 'A',
    reportingManagerUsername: member?.reportingManagerUsername || me,
    employeeCode: member?.employeeCode || '',
    designation: member?.designation || '',
  })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  // Nobody can report to themselves or to someone below them.
  const blocked = creating ? new Set() : peopleBelow(member.username, members)
  if (!creating) blocked.add(member.username)
  // Only Team Lead or higher, and never below this person's own level (from their designation).
  const personLevel = designationLevel(form.designation, member?.groupCode || 'EMPLOYEE')
  const ownTeam = managers.filter((m) => m.inMyTeam !== false && !blocked.has(m.username) && canReportTo(m, personLevel))
  const otherManagers = managers.filter((m) => m.inMyTeam === false && canReportTo(m, personLevel))
  const allowed = creating ? ownTeam : [...ownTeam, ...otherManagers]
  const reportsToOk = allowed.some((m) => m.username === form.reportingManagerUsername)
  const movingAway = !creating && otherManagers.some((m) => m.username === form.reportingManagerUsername)

  const save = async () => {
    if (!reportsToOk) {
      setFieldErrors({ reportingManagerUsername: `Choose someone at ${LEVEL_NAMES[Math.max(personLevel, LEVEL.TEAM_LEAD)]} level or higher` })
      return
    }
    setBusy(true)
    setError('')
    setFieldErrors({})
    try {
      if (creating) {
        await teamApi.create(form)
        notify(`Login created for ${form.username}`)
      } else {
        const body = {
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          status: form.status,
          reportingManagerUsername: form.reportingManagerUsername,
          employeeCode: form.employeeCode.trim(),
          designation: form.designation.trim(),
        }
        await teamApi.update(member.userId, body)
        notify(movingAway ? `${member.username} now reports to ${form.reportingManagerUsername}` : 'Saved')
      }
      onSaved()
    } catch (err) {
      setError(err.message)
      setFieldErrors(err.fieldErrors || {})
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{creating ? 'Add employee' : `Edit ${member.username}`}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
          {creating && (
            <TextField label="Username" required value={form.username} onChange={set('username')} error={Boolean(fieldErrors.username)} helperText={fieldErrors.username || 'Used to sign in'} />
          )}
          {creating && (
            <TextField
              label="Initial password"
              type="password"
              required
              value={form.password}
              onChange={set('password')}
              error={Boolean(fieldErrors.password)}
              helperText={fieldErrors.password || 'Temporary: they choose their own at first sign-in'}
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
          <EmployeeFields form={form} setForm={setForm} fieldErrors={fieldErrors} />
          <TextField
            select
            label="Reports to"
            value={reportsToOk ? form.reportingManagerUsername : ''}
            onChange={set('reportingManagerUsername')}
            error={Boolean(fieldErrors.reportingManagerUsername)}
            helperText={
              fieldErrors.reportingManagerUsername ||
              (reportsToOk ? 'Team Lead or higher, at least their own level' : 'Choose again: the previous choice is below their new level')
            }
          >
            <ListSubheader>Your team</ListSubheader>
            {ownTeam.map((m) => (
              <MenuItem key={m.userId} value={m.username}>
                {optionLabel(m, me)}
              </MenuItem>
            ))}
            {!creating && otherManagers.length > 0 && <ListSubheader>Other managers</ListSubheader>}
            {!creating &&
              otherManagers.map((m) => (
                <MenuItem key={m.userId} value={m.username}>
                  {optionLabel(m, me)}
                </MenuItem>
              ))}
          </TextField>
          <TextField select label="Status" value={form.status} onChange={set('status')}>
            <MenuItem value="A">Active</MenuItem>
            <MenuItem value="I">Inactive (cannot sign in)</MenuItem>
          </TextField>
        </Box>
        {movingAway && (
          <Alert severity="info" sx={{ mt: 2 }}>
            This person, their own team and any uploads waiting for review will move to {form.reportingManagerUsername}. You will no longer manage them.
          </Alert>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy}>
          {creating ? 'Create login' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function PasswordDialog({ member, onClose }) {
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
      await teamApi.resetPassword(member.userId, password)
      notify(`Password reset for ${member.username}`)
      onClose()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Reset password for {member.username}</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          fullWidth
          type="password"
          label="New password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={Boolean(error)}
          helperText={error || 'At least 8 characters. Share it with the employee securely.'}
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

export default function TeamPage() {
  const auth = useAuth()
  const loader = useCallback(() => Promise.all([teamApi.list(), teamApi.managers()]), [])
  const { data, loading, error, reload } = useAsync(loader)
  const [dialog, setDialog] = useState(null)
  const [members, managers] = data || [[], []]

  const closeAndReload = () => {
    setDialog(null)
    reload()
  }

  return (
    <Box>
      <PageHeader icon={TeamIcon}
        title="My team"
        subtitle="Everyone under you, including people who report to your team leads. Their uploads come to you for approval unless someone in between is a manager."
        actions={
          <Button variant="contained" onClick={() => setDialog({ type: 'member' })} disabled={!data}>
            Add employee
          </Button>
        }
      />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CircularProgress aria-label="Loading" />}
      {data && members.length === 0 && (
        <EmptyState title="No one reports to you yet" message="Add an employee, or ask an administrator to set you as someone's manager." />
      )}
      {data && members.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ bgcolor: '#FFFFFF' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Employee</TableCell>
                <TableCell>Employee ID</TableCell>
                <TableCell>Designation</TableCell>
                <TableCell>Reports to</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {members.map((m) => (
                <TableRow key={m.userId} hover>
                  <TableCell>
                    <Typography sx={{ fontWeight: 600 }}>{[m.firstName, m.lastName].filter(Boolean).join(' ') || m.username}</Typography>
                    <Typography variant="body2" color="text.secondary">{m.username}</Typography>
                  </TableCell>
                  <TableCell>{m.employeeCode || '—'}</TableCell>
                  <TableCell>{m.designation || '—'}</TableCell>
                  <TableCell>{m.reportingManagerUsername === auth.user?.username ? 'You' : m.reportingManagerName || m.reportingManagerUsername}</TableCell>
                  <TableCell>{m.email}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={m.status === 'A' ? 'Active' : 'Inactive'} color={m.status === 'A' ? 'success' : 'default'} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Button size="small" onClick={() => setDialog({ type: 'member', member: m })}>Edit</Button>
                    <Button size="small" onClick={() => setDialog({ type: 'password', member: m })}>Reset password</Button>
                    <Button size="small" color="error" onClick={() => setDialog({ type: 'delete', member: m })}>Delete</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {dialog?.type === 'member' && (
        <MemberDialog
          member={dialog.member}
          members={members}
          managers={managers}
          me={auth.user?.username}
          onClose={() => setDialog(null)}
          onSaved={closeAndReload}
        />
      )}
      {dialog?.type === 'password' && <PasswordDialog member={dialog.member} onClose={() => setDialog(null)} />}
      {dialog?.type === 'delete' && (
        <DeleteUserDialog
          person={dialog.member}
          remove={() => teamApi.remove(dialog.member.userId)}
          onClose={() => setDialog(null)}
          onDeleted={closeAndReload}
        />
      )}
    </Box>
  )
}
