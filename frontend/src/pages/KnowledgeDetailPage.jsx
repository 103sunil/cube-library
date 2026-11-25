import { useCallback, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { Alert, Box, Button, CircularProgress, Paper, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { knowledgeApi } from '../api/cube'
import { useAsync } from '../utils/useAsync'
import { formatDate, plural } from '../utils/format'
import { KeywordChips, StatusChip } from '../components/common'
import AttachmentList from '../components/AttachmentList'
import BookmarkButton from '../components/BookmarkButton'
import FileDropZone from '../components/FileDropZone'
import { ConfirmDialog, RejectDialog } from '../components/dialogs'
import { useNotify } from '../components/notify'

const CONFIRMS = {
  delete: {
    title: 'Delete this item?',
    message: 'The item, its keywords and all its attachments will be permanently deleted.',
    label: 'Delete',
    danger: true,
  },
  archive: {
    title: 'Archive this item?',
    message: 'It will be hidden from Discover and search. You can restore it later.',
    label: 'Archive',
  },
}

export default function KnowledgeDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const notify = useNotify()
  const loader = useCallback(() => knowledgeApi.get(id), [id])
  const { data: item, loading, error, reload } = useAsync(loader)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(null)
  const [rejecting, setRejecting] = useState(false)
  const [newFiles, setNewFiles] = useState([])

  const run = async (action, message) => {
    setBusy(true)
    try {
      await action()
      notify(message)
      setConfirm(null)
      setRejecting(false)
      reload()
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await knowledgeApi.remove(id)
      notify('Item deleted')
      navigate('/', { replace: true })
    } catch (err) {
      notify(err.message, 'error')
      setBusy(false)
    }
  }

  const uploadFiles = () =>
    run(async () => {
      await knowledgeApi.addAttachments(id, newFiles)
      setNewFiles([])
    }, `${plural(newFiles.length, 'file')} uploaded`)

  if (loading && !item) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress aria-label="Loading" />
      </Box>
    )
  }
  if (error) {
    return (
      <Box>
        <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
          Back to Discover
        </Button>
        <Alert severity="error">{error}</Alert>
      </Box>
    )
  }
  if (!item) return null

  const a = item.actions || {}
  const isResubmit = item.status === 'REJECTED'

  return (
    <Box>
      <Button onClick={() => navigate(-1)} startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
        Back
      </Button>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', mb: 1 }}>
        <Box sx={{ minWidth: 0, flex: '1 1 420px' }}>
          <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>
            {item.title}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 1, flexWrap: 'wrap' }}>
            <StatusChip status={item.status} />
            <Typography color="text.secondary" variant="body2">
              by {item.ownerName || item.ownerUsername} · created {formatDate(item.createdAt)}
              {item.publishedAt ? ` · published ${formatDate(item.publishedAt)}` : ''}
              {item.reviewedBy ? ` · reviewed by ${item.reviewedBy}` : ''}
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start' }}>
          {item.status === 'PUBLISHED' && (
            <BookmarkButton key={`${item.knowledgeId}-${item.bookmarked}`} knowledgeId={item.knowledgeId} initial={item.bookmarked} />
          )}
          {a.canEdit && (
            <Button variant="outlined" component={RouterLink} to={`/knowledge/${item.knowledgeId}/edit`} disabled={busy}>
              Edit
            </Button>
          )}
          {a.canSubmit && (
            <Button
              variant="contained"
              disabled={busy}
              onClick={() => run(() => knowledgeApi.submit(id), isResubmit ? 'Resubmitted for approval' : 'Submitted')}
            >
              {isResubmit ? 'Resubmit' : 'Submit for approval'}
            </Button>
          )}
          {a.canApprove && (
            <Button variant="contained" color="success" disabled={busy} onClick={() => run(() => knowledgeApi.approve(id), 'Approved and published')}>
              Approve
            </Button>
          )}
          {a.canReject && (
            <Button variant="outlined" color="error" disabled={busy} onClick={() => setRejecting(true)}>
              Reject
            </Button>
          )}
          {a.canArchive && (
            <Button variant="outlined" disabled={busy} onClick={() => setConfirm('archive')}>
              Archive
            </Button>
          )}
          {a.canRestore && (
            <Button variant="outlined" disabled={busy} onClick={() => run(() => knowledgeApi.restore(id), 'Restored')}>
              Restore
            </Button>
          )}
          {a.canDelete && (
            <Button variant="outlined" color="error" disabled={busy} onClick={() => setConfirm('delete')}>
              Delete
            </Button>
          )}
        </Box>
      </Box>

      {item.status === 'REJECTED' && item.rejectReason && (
        <Alert severity="error" sx={{ mt: 2 }}>
          <strong>Rejected{item.reviewedBy ? ` by ${item.reviewedBy}` : ''}:</strong> {item.rejectReason}
          {a.canEdit && ' — edit the item and resubmit it.'}
        </Alert>
      )}
      {item.status === 'PENDING' && !a.canApprove && (
        <Alert severity="info" sx={{ mt: 2 }}>
          {item.assignedReviewerName
            ? `Waiting for review by ${item.assignedReviewerName}.`
            : 'Waiting for a manager to review this submission.'}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 3, mt: 3, bgcolor: '#FFFFFF' }}>
        <Typography variant="h6" sx={{ mb: 1.5 }}>
          Description
        </Typography>
        {item.description ? (
          <Typography component="div" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', lineHeight: 1.7 }}>
            {item.description}
          </Typography>
        ) : (
          <Typography color="text.secondary">No description yet.</Typography>
        )}
        {item.keywords?.length > 0 && (
          <Box sx={{ mt: 2.5 }}>
            <KeywordChips keywords={item.keywords} />
          </Box>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 3, mt: 3, bgcolor: '#FFFFFF' }}>
        <Typography variant="h6" sx={{ mb: 1.5 }}>
          Attachments ({item.attachmentCount})
        </Typography>
        <AttachmentList
          attachments={item.attachments}
          canDownload={a.canDownload}
          canDelete={a.canManageAttachments}
          onDeleted={reload}
        />
        {a.canManageAttachments && (
          <Box sx={{ mt: 3 }}>
            <FileDropZone files={newFiles} onChange={setNewFiles} disabled={busy} />
            {newFiles.length > 0 && (
              <Button variant="contained" sx={{ mt: 1 }} disabled={busy} onClick={uploadFiles}>
                Upload {plural(newFiles.length, 'file')}
              </Button>
            )}
          </Box>
        )}
      </Paper>

      <ConfirmDialog
        open={Boolean(confirm)}
        title={confirm ? CONFIRMS[confirm].title : ''}
        message={confirm ? CONFIRMS[confirm].message : ''}
        confirmLabel={confirm ? CONFIRMS[confirm].label : ''}
        danger={confirm === 'delete'}
        busy={busy}
        onClose={() => setConfirm(null)}
        onConfirm={() => (confirm === 'delete' ? remove() : run(() => knowledgeApi.archive(id), 'Archived'))}
      />
      <RejectDialog
        open={rejecting}
        itemTitle={item.title}
        busy={busy}
        onClose={() => setRejecting(false)}
        onSubmit={(reason) => run(() => knowledgeApi.reject(id, reason), 'Rejected. The author has been given your reason.')}
      />
    </Box>
  )
}
