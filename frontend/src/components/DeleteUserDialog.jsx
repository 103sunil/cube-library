import { useState } from 'react'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { useNotify } from './notify'

/**
 * Confirms deleting someone who left the company. `remove` calls the API and resolves to
 * { reportsMoved, reportsMovedTo }.
 */
export default function DeleteUserDialog({ person, remove, onClose, onDeleted }) {
  const notify = useNotify()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const name = [person.firstName, person.lastName].filter(Boolean).join(' ') || person.username

  const confirm = async () => {
    setBusy(true)
    setError('')
    try {
      const result = await remove()
      const moved = result?.reportsMoved
        ? ` ${result.reportsMoved} people who reported to them now report to ${result.reportsMovedTo || 'no one (top of hierarchy)'}.`
        : ''
      notify(`${person.username} deleted.${moved}`)
      onDeleted()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Delete {name}?</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Typography>Use this when someone leaves the company.</Typography>
        <Box component="ul" sx={{ pl: 2.5, mb: 0, color: 'text.secondary' }}>
          <li>{person.username} can no longer sign in and disappears from the lists.</li>
          <li>Knowledge they published stays available.</li>
          <li>Anyone who reports to them moves up to their manager, with any uploads waiting for approval.</li>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" color="error" onClick={confirm} disabled={busy}>
          {busy ? 'Deleting…' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
