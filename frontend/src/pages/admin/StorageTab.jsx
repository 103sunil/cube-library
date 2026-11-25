import { useCallback, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Paper, Typography } from '@mui/material'
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import { adminApi } from '../../api/cube'
import { useAsync } from '../../utils/useAsync'
import { useNotify } from '../../components/notify'

const LOCATIONS = [
  { key: 'github', label: 'On GitHub' },
  { key: 'local', label: 'Server disk (cube-files)' },
  { key: 'legacyDisk', label: 'Old version disk paths' },
  { key: 'database', label: 'Database (old version)' },
]

/** Where attachments live, and a one-click copy of everything to GitHub. */
export default function StorageTab() {
  const notify = useNotify()
  const loader = useCallback(() => adminApi.storageStatus(), [])
  const { data, loading, error, reload } = useAsync(loader)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [actionError, setActionError] = useState('')

  const migrate = async () => {
    setBusy(true)
    setActionError('')
    setResult(null)
    try {
      const r = await adminApi.migrateStorage()
      setResult(r)
      notify(`${r.copied} file(s) copied to GitHub`)
      reload()
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) return <CircularProgress aria-label="Loading" />
  if (error) return <Alert severity="error">{error}</Alert>
  if (!data) return null

  const isGitHub = data.storageType === 'github'
  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      <Paper variant="outlined" sx={{ p: 3, bgcolor: '#FFFFFF' }}>
        <Typography variant="h6">New uploads go to</Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 1, flexWrap: 'wrap' }}>
          <Chip label={isGitHub ? 'GitHub' : 'Server disk'} color="primary" />
          {isGitHub && (
            <Chip
              variant="outlined"
              color={data.githubConfigured ? 'success' : 'warning'}
              label={data.githubConfigured ? 'GitHub connected' : 'GitHub details missing'}
            />
          )}
        </Box>
        {isGitHub && !data.githubConfigured && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            Uploads and downloads of GitHub files will fail until the server is started with CUBE_GITHUB_OWNER,
            CUBE_GITHUB_REPO and CUBE_GITHUB_TOKEN.
          </Alert>
        )}
        {!isGitHub && (
          <Alert severity="info" sx={{ mt: 2 }}>
            To move to GitHub, start the server with CUBE_STORAGE_TYPE=github plus the GitHub details, then come back
            here and copy the existing files.
          </Alert>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 3, bgcolor: '#FFFFFF' }}>
        <Typography variant="h6">Existing attachments</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 2, mt: 2 }}>
          {LOCATIONS.map((l) => (
            <Box key={l.key} sx={{ p: 2, borderRadius: 3, bgcolor: '#F8FAFC' }}>
              <Typography variant="h4" component="p" sx={{ fontWeight: 700 }}>{data.counts[l.key]}</Typography>
              <Typography variant="body2" color="text.secondary">{l.label}</Typography>
            </Box>
          ))}
        </Box>
        <Box sx={{ mt: 3, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          <Button
            variant="contained"
            startIcon={busy ? <CircularProgress size={16} color="inherit" /> : <CloudUploadOutlinedIcon />}
            disabled={busy || !isGitHub || !data.githubConfigured || data.notOnGitHub === 0}
            onClick={migrate}
          >
            {busy ? 'Copying… (this can take a few minutes)' : `Copy ${data.notOnGitHub} file(s) to GitHub`}
          </Button>
          <Typography variant="body2" color="text.secondary">
            Originals are kept. Safe to run again; only files not yet on GitHub are copied.
          </Typography>
        </Box>
        {actionError && <Alert severity="error" sx={{ mt: 2 }}>{actionError}</Alert>}
        {result && (
          <Alert severity={result.errors.length ? 'warning' : 'success'} sx={{ mt: 2 }}>
            {result.copied} copied{result.skipped ? `, ${result.skipped} skipped (deleted items)` : ''}.
            {result.errors.length > 0 && (
              <Box component="ul" sx={{ m: 0, mt: 1, pl: 2.5 }}>
                {result.errors.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </Box>
            )}
          </Alert>
        )}
      </Paper>
    </Box>
  )
}
