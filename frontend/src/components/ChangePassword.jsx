import { useState } from 'react'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { authApi } from '../api/cube'

/** Current + new + confirm fields. Calls onDone() after the password was changed. */
export function ChangePasswordForm({ onDone, onCancel, submitLabel = 'Change password' }) {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    if (!form.current) found.current = 'Enter your current password'
    if (form.next.length < 8) found.next = 'At least 8 characters'
    else if (form.next === form.current) found.next = 'Choose a different password'
    if (form.confirm !== form.next) found.confirm = 'Passwords do not match'
    setErrors(found)
    if (Object.keys(found).length) return
    setBusy(true)
    setError('')
    try {
      await authApi.changePassword(form.current, form.next)
      await onDone()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Box component="form" onSubmit={submit} noValidate sx={{ display: 'grid', gap: 2, pt: 1 }}>
      {error && <Alert severity="error">{error}</Alert>}
      <TextField
        label="Current password"
        type="password"
        autoComplete="current-password"
        autoFocus
        value={form.current}
        onChange={set('current')}
        error={Boolean(errors.current)}
        helperText={errors.current}
      />
      <TextField
        label="New password"
        type="password"
        autoComplete="new-password"
        value={form.next}
        onChange={set('next')}
        error={Boolean(errors.next)}
        helperText={errors.next || 'At least 8 characters'}
      />
      <TextField
        label="Confirm new password"
        type="password"
        autoComplete="new-password"
        value={form.confirm}
        onChange={set('confirm')}
        error={Boolean(errors.confirm)}
        helperText={errors.confirm}
      />
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
        {onCancel && <Button onClick={onCancel} disabled={busy}>Cancel</Button>}
        <Button type="submit" variant="contained" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
      </Box>
    </Box>
  )
}

export function ChangePasswordDialog({ onClose, onChanged }) {
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Change password</DialogTitle>
      <DialogContent>
        <ChangePasswordForm onCancel={onClose} onDone={onChanged} />
      </DialogContent>
      <DialogActions />
    </Dialog>
  )
}
