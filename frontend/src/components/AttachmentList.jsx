import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material'
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined'
import DownloadIcon from '@mui/icons-material/Download'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'
import { attachmentApi } from '../api/cube'
import { previewKind, saveBlob } from '../utils/files'
import { formatBytes, formatDate } from '../utils/format'
import { useNotify } from './notify'
import { ConfirmDialog } from './dialogs'
import { ExcelPreview, PowerPointPreview, WordPreview } from './OfficePreview'

const OFFICE_VIEWERS = { word: WordPreview, excel: ExcelPreview, powerpoint: PowerPointPreview }

function PreviewDialog({ attachment, onClose, onDownload }) {
  const [state, setState] = useState({ loading: true, error: '', url: '', text: '', blob: null })
  const kind = previewKind(attachment.contentType, attachment.fileName)
  const OfficeViewer = OFFICE_VIEWERS[kind]

  useEffect(() => {
    let cancelled = false
    let objectUrl = ''
    attachmentApi
      .blob(attachment.attachmentId, OFFICE_VIEWERS[kind] ? 'download' : 'preview')
      .then(async (blob) => {
        if (cancelled) return
        if (OFFICE_VIEWERS[kind]) {
          setState({ loading: false, error: '', url: '', text: '', blob })
        } else if (kind === 'text') {
          const text = await blob.text()
          if (!cancelled) setState({ loading: false, error: '', url: '', text })
        } else {
          objectUrl = URL.createObjectURL(new Blob([blob], { type: attachment.contentType }))
          setState({ loading: false, error: '', url: objectUrl, text: '' })
        }
      })
      .catch((error) => !cancelled && setState({ loading: false, error: error.message, url: '', text: '' }))
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [attachment, kind])

  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle sx={{ overflowWrap: 'anywhere' }}>{attachment.fileName}</DialogTitle>
      <DialogContent
        dividers
        sx={{ minHeight: 300, display: 'flex', justifyContent: 'center', alignItems: OfficeViewer ? 'flex-start' : 'center' }}
      >
        {state.loading && <CircularProgress aria-label="Loading preview" />}
        {state.error && <Alert severity="error">{state.error}</Alert>}
        {!state.loading && !state.error && OfficeViewer && state.blob && <OfficeViewer blob={state.blob} />}
        {!state.loading && !state.error && kind === 'image' && (
          <Box component="img" src={state.url} alt={attachment.fileName} sx={{ maxWidth: '100%', maxHeight: '75vh' }} />
        )}
        {!state.loading && !state.error && kind === 'pdf' && (
          <Box component="iframe" title={attachment.fileName} src={state.url} sx={{ width: '100%', height: '75vh', border: 0 }} />
        )}
        {!state.loading && !state.error && kind === 'text' && (
          <Box component="pre" sx={{ width: '100%', m: 0, whiteSpace: 'pre-wrap', fontFamily: 'Consolas, monospace', fontSize: 14 }}>
            {state.text}
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 1.5 }}>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" startIcon={<DownloadIcon />} onClick={onDownload}>
          Download
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/**
 * Attachments of a knowledge item. Downloads go through fetch with the auth header,
 * using the numeric attachmentId from the API.
 */
export default function AttachmentList({ attachments, canDownload, canDelete, onDeleted }) {
  const notify = useNotify()
  const [busyId, setBusyId] = useState(null)
  const [previewing, setPreviewing] = useState(null)
  const [deleting, setDeleting] = useState(null)

  const download = async (attachment) => {
    setBusyId(attachment.attachmentId)
    try {
      const blob = await attachmentApi.blob(attachment.attachmentId, 'download')
      saveBlob(blob, attachment.fileName)
    } catch (error) {
      notify(error.message, 'error')
    } finally {
      setBusyId(null)
    }
  }

  const confirmDelete = async () => {
    const attachment = deleting
    setBusyId(attachment.attachmentId)
    try {
      await attachmentApi.remove(attachment.attachmentId)
      notify(`Removed ${attachment.fileName}`)
      setDeleting(null)
      onDeleted?.(attachment)
    } catch (error) {
      notify(error.message, 'error')
    } finally {
      setBusyId(null)
    }
  }

  if (!attachments || attachments.length === 0) {
    return <Typography color="text.secondary">No attachments.</Typography>
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {attachments.map((attachment) => {
        const busy = busyId === attachment.attachmentId
        return (
          <Paper
            key={attachment.attachmentId}
            variant="outlined"
            sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, bgcolor: '#FFFFFF' }}
          >
            <InsertDriveFileOutlinedIcon color="action" />
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{attachment.fileName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {formatBytes(attachment.fileSize)} · added {formatDate(attachment.createdAt)}
              </Typography>
            </Box>
            {busy && <CircularProgress size={20} />}
            {canDownload && previewKind(attachment.contentType, attachment.fileName) && (
              <Tooltip title="Preview">
                <span>
                  <IconButton aria-label={`Preview ${attachment.fileName}`} disabled={busy} onClick={() => setPreviewing(attachment)}>
                    <VisibilityOutlinedIcon />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {canDownload && (
              <Tooltip title="Download">
                <span>
                  <IconButton aria-label={`Download ${attachment.fileName}`} disabled={busy} onClick={() => download(attachment)}>
                    <DownloadIcon />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {canDelete && (
              <Tooltip title="Remove">
                <span>
                  <IconButton aria-label={`Remove ${attachment.fileName}`} disabled={busy} onClick={() => setDeleting(attachment)}>
                    <DeleteOutlineIcon />
                  </IconButton>
                </span>
              </Tooltip>
            )}
          </Paper>
        )
      })}

      {previewing && (
        <PreviewDialog attachment={previewing} onClose={() => setPreviewing(null)} onDownload={() => download(previewing)} />
      )}
      <ConfirmDialog
        open={Boolean(deleting)}
        title="Remove attachment?"
        message={deleting ? `"${deleting.fileName}" will be permanently removed.` : ''}
        confirmLabel="Remove"
        danger
        busy={Boolean(busyId)}
        onConfirm={confirmDelete}
        onClose={() => setDeleting(null)}
      />
    </Box>
  )
}
