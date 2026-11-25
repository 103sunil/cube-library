import { useCallback, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
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
import { adminApi } from '../../api/cube'
import { useAuth } from '../../auth/AuthContext'
import { useAsync } from '../../utils/useAsync'
import { MODULE_LABELS, PERM, permissionLabel } from '../../config'
import { ConfirmDialog } from '../../components/dialogs'
import { useNotify } from '../../components/notify'

async function loadAll() {
  const [groups, accessCodes] = await Promise.all([adminApi.groups(), adminApi.accessCodes()])
  const grants = await Promise.all(groups.map((g) => adminApi.grants(g.groupId)))
  return {
    accessCodes: accessCodes.filter((a) => a.status !== 'I'),
    groups: groups.map((group, i) => ({
      ...group,
      granted: grants[i].filter((grant) => grant.allowed && grant.status !== 'I').map((grant) => grant.permission),
      grantCodes: grants[i].map((grant) => grant.permission),
    })),
  }
}

function byModule(accessCodes) {
  const map = new Map()
  for (const a of accessCodes) {
    if (!map.has(a.moduleCode)) map.set(a.moduleCode, [])
    map.get(a.moduleCode).push(a)
  }
  return [...map.entries()]
}

function GroupDialog({ group, accessCodes, canEditGroup, canEditGrants, onClose, onSaved }) {
  const notify = useNotify()
  const creating = !group
  const [form, setForm] = useState({
    groupCode: '',
    groupName: group?.groupName || '',
    description: group?.description || '',
    status: group?.status || 'A',
  })
  const [selected, setSelected] = useState(() => new Set(group?.granted || []))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const toggle = (code) =>
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })

  const save = async () => {
    setBusy(true)
    setError('')
    try {
      let saved = group
      if (creating) {
        saved = await adminApi.createGroup(form)
      } else if (canEditGroup) {
        saved = await adminApi.updateGroup(group.groupId, {
          groupName: form.groupName,
          description: form.description,
          status: form.status,
        })
      }
      if (canEditGrants) {
        const before = new Set(group?.granted || [])
        const existing = new Set(group?.grantCodes || [])
        for (const a of accessCodes) {
          const want = selected.has(a.permission)
          if (want && !before.has(a.permission)) {
            await adminApi.upsertGrant(saved.groupId, { moduleCode: a.moduleCode, accessCode: a.code, allowed: true })
          } else if (!want && existing.has(a.permission)) {
            await adminApi.deleteGrant(saved.groupId, a.moduleCode, a.code)
          }
        }
      }
      notify(creating ? `Group ${saved.groupName} created` : 'Group saved')
      onSaved()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{creating ? 'New group' : `Edit ${group.groupName}`}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
          {creating && (
            <TextField label="Code" required value={form.groupCode} onChange={set('groupCode')} helperText="Letters, digits and _ (e.g. TEAM_LEAD)" />
          )}
          <TextField label="Name" required value={form.groupName} onChange={set('groupName')} disabled={!canEditGroup} />
          <TextField select label="Status" value={form.status} onChange={set('status')} disabled={!canEditGroup}>
            <MenuItem value="A">Active</MenuItem>
            <MenuItem value="I">Inactive</MenuItem>
          </TextField>
          <TextField
            label="Description"
            value={form.description}
            onChange={set('description')}
            disabled={!canEditGroup}
            sx={{ gridColumn: '1 / -1' }}
          />
        </Box>
        <Typography sx={{ fontWeight: 600, mt: 3 }}>Permissions for members of this group</Typography>
        {!canEditGrants && (
          <Typography variant="body2" color="text.secondary">
            You can view but not change permissions.
          </Typography>
        )}
        {byModule(accessCodes).map(([moduleCode, codes]) => (
          <Box key={moduleCode} sx={{ mt: 1.5 }}>
            <Typography variant="overline" color="text.secondary">
              {MODULE_LABELS[moduleCode] || moduleCode}
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column' }}>
              {codes.map((a) => (
                <FormControlLabel
                  key={a.permission}
                  disabled={!canEditGrants}
                  control={<Checkbox checked={selected.has(a.permission)} onChange={() => toggle(a.permission)} />}
                  label={
                    <Box>
                      <Typography component="span" sx={{ fontWeight: 600 }}>{permissionLabel(a.permission, a.code)}</Typography>
                      {a.description && (
                        <Typography component="span" color="text.secondary" variant="body2">{` — ${a.description}`}</Typography>
                      )}
                    </Box>
                  }
                />
              ))}
            </Box>
          </Box>
        ))}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy}>
          {creating ? 'Create group' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default function GroupsTab() {
  const auth = useAuth()
  const notify = useNotify()
  const canEditGroup = auth.can(PERM.MANAGE_GROUPS)
  const canEditGrants = auth.can(PERM.MANAGE_GROUP_ACCESS)
  const loader = useCallback(() => loadAll(), [])
  const { data, loading, error, reload } = useAsync(loader)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [busy, setBusy] = useState(false)

  const confirmDelete = async () => {
    setBusy(true)
    try {
      await adminApi.deleteGroup(deleting.groupId)
      notify('Group deleted')
      setDeleting(null)
      reload()
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const labelOf = (code) => {
    const a = data?.accessCodes.find((x) => x.permission === code)
    return permissionLabel(code, a?.code)
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Typography color="text.secondary">
          Each user belongs to one group. The group decides what its members can do.
        </Typography>
        {canEditGroup && <Button variant="contained" onClick={() => setEditing({})}>New group</Button>}
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CircularProgress aria-label="Loading" />}
      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ bgcolor: '#FFFFFF' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Group</TableCell>
                <TableCell>Members</TableCell>
                <TableCell>Permissions</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.groups.map((g) => (
                <TableRow key={g.groupId} hover>
                  <TableCell>
                    <Typography sx={{ fontWeight: 600 }}>
                      {g.groupName} {g.status === 'I' && <Chip size="small" label="Inactive" sx={{ ml: 1 }} />}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">{g.groupCode}</Typography>
                  </TableCell>
                  <TableCell>{g.memberCount}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {g.granted.map((code) => (
                        <Chip key={code} size="small" label={labelOf(code)} />
                      ))}
                    </Box>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {(canEditGroup || canEditGrants) && (
                      <Button size="small" onClick={() => setEditing({ group: g })}>Edit</Button>
                    )}
                    {canEditGroup && (
                      <Button size="small" color="error" onClick={() => setDeleting(g)}>Delete</Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {editing && data && (
        <GroupDialog
          group={editing.group}
          accessCodes={data.accessCodes}
          canEditGroup={canEditGroup}
          canEditGrants={canEditGrants}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reload()
          }}
        />
      )}
      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete group?"
        message={deleting ? `"${deleting.groupName}" will be deleted. Groups that still have members cannot be deleted.` : ''}
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={confirmDelete}
        onClose={() => setDeleting(null)}
      />
    </Box>
  )
}
