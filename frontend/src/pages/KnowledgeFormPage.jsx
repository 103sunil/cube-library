import SubmitIcon from '@mui/icons-material/NoteAddOutlined'
import EditIcon from '@mui/icons-material/EditNoteOutlined'
import { useCallback, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { Alert, Box, Button, CircularProgress, Paper, TextField, Typography } from '@mui/material'
import { knowledgeApi } from '../api/cube'
import { useAuth } from '../auth/AuthContext'
import { MAX_TITLE_LENGTH, PERM } from '../config'
import { useAsync } from '../utils/useAsync'
import { plural } from '../utils/format'
import KeywordInput from '../components/KeywordInput'
import FileDropZone from '../components/FileDropZone'
import AttachmentList from '../components/AttachmentList'
import { PageHeader } from '../components/common'
import { useNotify } from '../components/notify'

function KnowledgeForm({ item, onAttachmentDeleted }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const notify = useNotify()
  const editing = Boolean(item)
  const [title, setTitle] = useState(item?.title || '')
  const [description, setDescription] = useState(item?.description || '')
  const [keywords, setKeywords] = useState(item?.keywords || [])
  const [files, setFiles] = useState([])
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [busy, setBusy] = useState(false)

  const canApprove = auth.can(PERM.APPROVE)
  const actions = item?.actions || {}
  const canManageFiles = !editing || actions.canManageAttachments

  const existingFiles = item?.attachments?.length ?? item?.attachmentCount ?? 0

  /** Drafts only need a title. Anything submitted or published needs all four fields. */
  const validate = (complete) => {
    const next = {}
    if (!title.trim()) next.title = 'Title is required'
    else if (title.trim().length > MAX_TITLE_LENGTH) next.title = `At most ${MAX_TITLE_LENGTH} characters`
    if (complete && !description.trim()) next.description = 'Description is required'
    if (complete && keywords.length === 0) next.keywords = 'Add at least one keyword'
    if (complete && existingFiles + files.length === 0) next.files = 'Attach at least one file'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const fail = (error) => {
    setServerError(error.message)
    if (error.fieldErrors) setErrors(error.fieldErrors)
    setBusy(false)
  }

  const create = async (draft) => {
    if (!validate(!draft)) return
    setBusy(true)
    setServerError('')
    try {
      const created = await knowledgeApi.create({ title: title.trim(), description, keywords, files, draft })
      const message = draft
        ? 'Draft saved'
        : created.status === 'PUBLISHED'
          ? 'Published'
          : 'Submitted for approval'
      notify(message)
      navigate(`/knowledge/${created.knowledgeId}`, { replace: true })
    } catch (error) {
      fail(error)
    }
  }

  const save = async (submitAfter) => {
    if (!validate(submitAfter || item.status !== 'DRAFT')) return
    setBusy(true)
    setServerError('')
    try {
      await knowledgeApi.update(item.knowledgeId, { title: title.trim(), description, keywords })
      if (files.length) await knowledgeApi.addAttachments(item.knowledgeId, files)
      if (submitAfter) await knowledgeApi.submit(item.knowledgeId)
      notify(submitAfter ? (item.status === 'REJECTED' ? 'Resubmitted for approval' : 'Submitted') : 'Changes saved')
      navigate(`/knowledge/${item.knowledgeId}`, { replace: true })
    } catch (error) {
      fail(error)
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2.5, md: 4 }, bgcolor: '#FFFFFF' }}>
      {item?.status === 'REJECTED' && item.rejectReason && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <strong>Rejection reason:</strong> {item.rejectReason}
        </Alert>
      )}
      {serverError && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {serverError}
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <TextField
          label="Title"
          required
          fullWidth
          value={title}
          disabled={busy}
          onChange={(e) => setTitle(e.target.value)}
          error={Boolean(errors.title)}
          helperText={errors.title || `${title.length}/${MAX_TITLE_LENGTH}`}
          slotProps={{ htmlInput: { maxLength: MAX_TITLE_LENGTH } }}
        />
        <TextField
          label="Description"
          required
          fullWidth
          multiline
          minRows={8}
          value={description}
          disabled={busy}
          onChange={(e) => setDescription(e.target.value)}
          error={Boolean(errors.description)}
          helperText={errors.description || 'Explain the solution step by step. Line breaks are kept.'}
        />
        <KeywordInput value={keywords} onChange={setKeywords} disabled={busy} required externalError={errors.keywords} />

        <Box>
          <Typography sx={{ fontWeight: 600, mb: 1 }}>
            Attachments <Box component="span" sx={{ color: 'error.main' }} aria-hidden>*</Box>
          </Typography>
          {errors.files && existingFiles + files.length === 0 && (
            <Alert severity="error" sx={{ mb: 1.5 }}>{errors.files}</Alert>
          )}
          {editing && (
            <Box sx={{ mb: 2 }}>
              <AttachmentList
                attachments={item.attachments}
                canDownload={actions.canDownload}
                canDelete={actions.canManageAttachments}
                onDeleted={onAttachmentDeleted}
              />
            </Box>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Drafts can be saved without keywords or files; both are required to submit.
          </Typography>
          {canManageFiles ? (
            <FileDropZone files={files} onChange={setFiles} disabled={busy} existingCount={item?.attachmentCount || 0} />
          ) : (
            <Typography color="text.secondary" variant="body2">
              Attachments cannot be changed while the item is {item.status.toLowerCase()}.
            </Typography>
          )}
        </Box>
      </Box>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1.5, mt: 4, flexWrap: 'wrap' }}>
        {busy && <CircularProgress size={28} sx={{ alignSelf: 'center' }} aria-label="Saving" />}
        {editing ? (
          <>
            <Button component={RouterLink} to={`/knowledge/${item.knowledgeId}`} disabled={busy}>
              Cancel
            </Button>
            <Button variant={actions.canSubmit ? 'outlined' : 'contained'} disabled={busy} onClick={() => save(false)}>
              Save changes
            </Button>
            {actions.canSubmit && (
              <Button variant="contained" disabled={busy} onClick={() => save(true)}>
                {item.status === 'REJECTED' ? 'Save and resubmit' : 'Save and submit'}
              </Button>
            )}
          </>
        ) : (
          <>
            <Button variant="outlined" disabled={busy} onClick={() => create(true)}>
              Save as draft
            </Button>
            <Button variant="contained" disabled={busy} onClick={() => create(false)}>
              {canApprove ? 'Publish' : 'Submit for approval'}
              {files.length ? ` (${plural(files.length, 'file')})` : ''}
            </Button>
          </>
        )}
      </Box>
    </Paper>
  )
}

export function SubmitPage() {
  const auth = useAuth()
  return (
    <Box>
      <PageHeader
        icon={SubmitIcon}
        title="Submit knowledge"
        subtitle={
          auth.can(PERM.APPROVE)
            ? 'Share a solution, guide or document. As a manager, your submission is published immediately.'
            : 'Share a solution, guide or document. A manager reviews it before it is published.'
        }
      />
      <KnowledgeForm />
    </Box>
  )
}

export function EditPage() {
  const { id } = useParams()
  const loader = useCallback(() => knowledgeApi.get(id), [id])
  const { data: item, loading, error, reload } = useAsync(loader)

  if (loading && !item) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress aria-label="Loading" />
      </Box>
    )
  }
  if (error) return <Alert severity="error">{error}</Alert>
  if (!item) return null
  if (!item.actions?.canEdit) {
    return (
      <Alert severity="warning">
        This item can&apos;t be edited in its current status ({item.status.toLowerCase()}).{' '}
        <RouterLink to={`/knowledge/${item.knowledgeId}`}>Back to the item</RouterLink>
      </Alert>
    )
  }
  return (
    <Box>
      <PageHeader icon={EditIcon} title="Edit knowledge" subtitle={item.title} />
      <KnowledgeForm key={item.knowledgeId} item={item} onAttachmentDeleted={reload} />
    </Box>
  )
}
