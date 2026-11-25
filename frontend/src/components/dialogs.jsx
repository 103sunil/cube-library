import { useState } from 'react'
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material'

export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', danger = false, busy, onConfirm, onClose }) {
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="contained" color={danger ? 'error' : 'primary'} onClick={onConfirm} disabled={busy}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Asks for a rejection reason; the reason is shown to the author. */
export function RejectDialog({ open, itemTitle, busy, onSubmit, onClose }) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const missing = !reason.trim()

  const close = () => {
    setReason('')
    setTouched(false)
    onClose()
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : close} maxWidth="sm" fullWidth>
      <DialogTitle>Reject submission</DialogTitle>
      <DialogContent>
        {itemTitle && <DialogContentText sx={{ mb: 2 }}>&ldquo;{itemTitle}&rdquo;</DialogContentText>}
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={3}
          label="Reason (shown to the author)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          onBlur={() => setTouched(true)}
          error={touched && missing}
          helperText={touched && missing ? 'A reason is required' : `${reason.length}/1000`}
          slotProps={{ htmlInput: { maxLength: 1000 } }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={close} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color="error"
          disabled={busy}
          onClick={() => {
            setTouched(true)
            if (!missing) onSubmit(reason.trim())
          }}
        >
          Reject
        </Button>
      </DialogActions>
    </Dialog>
  )
}
