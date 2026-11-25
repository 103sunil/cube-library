import { useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import { adminApi } from '../../api/cube'

const TEMPLATE =
  'username,email,firstName,lastName,employeeId,designation,group,reportingManager\n' +
  'asha.manager,asha@company.com,Asha,Rao,M1001,Engineering Manager,MANAGER,\n' +
  'ravi.k,ravi@company.com,Ravi,Kumar,E2001,Team Lead,EMPLOYEE,asha.manager\n' +
  'neha.s,neha@company.com,Neha,Singh,E2002,Employee,EMPLOYEE,asha.manager\n'

function downloadText(name, text) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Bulk onboarding: managers and their teams from one CSV (Excel → Save as CSV). */
export default function ImportUsersDialog({ onClose }) {
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  const upload = async () => {
    setBusy(true)
    setError('')
    try {
      setResult(await adminApi.importUsers(file))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const passwordsCsv = () =>
    'username,employeeId,group,reportingManager,temporaryPassword\n' +
    result.users
      .map((u) => [u.username, u.employeeCode || '', u.group, u.reportingManager || '', u.temporaryPassword].join(','))
      .join('\n')

  const created = result && result.created > 0

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>Import users from CSV</DialogTitle>
      <DialogContent>
        {!created && (
          <>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              One row per person. <b>group</b> is EMPLOYEE, MANAGER or ADMIN. <b>reportingManager</b> is the
              manager&apos;s username; the manager may be in the same file. Everyone gets a temporary password and
              chooses their own at first sign-in. If any row has a problem, nobody is created.
            </Typography>
            <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mb: 2 }}>
              <Button onClick={() => downloadText('cube-users-template.csv', TEMPLATE)}>Download template</Button>
              <Button component="label" variant="outlined" startIcon={<UploadFileOutlinedIcon />}>
                Choose CSV file
                <input
                  hidden
                  type="file"
                  accept=".csv,text/csv"
                  aria-label="CSV file"
                  onChange={(e) => {
                    setFile(e.target.files?.[0] || null)
                    setResult(null)
                  }}
                />
              </Button>
              {file && <Typography variant="body2">{file.name}</Typography>}
            </Box>
          </>
        )}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {result?.errors?.length > 0 && (
          <Alert severity="error" sx={{ mb: 2 }}>
            <Typography sx={{ fontWeight: 600 }}>Nothing was imported. Fix these rows and try again:</Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              {result.errors.map((e) => (
                <li key={e}>{e}</li>
              ))}
            </Box>
          </Alert>
        )}
        {created && (
          <>
            <Alert severity="success" sx={{ mb: 2 }}>
              {result.created} users created. Save the temporary passwords now; they are not shown again.
            </Alert>
            <TableContainer sx={{ maxHeight: 360 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Username</TableCell>
                    <TableCell>Employee ID</TableCell>
                    <TableCell>Group</TableCell>
                    <TableCell>Reports to</TableCell>
                    <TableCell>Temporary password</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {result.users.map((u) => (
                    <TableRow key={u.username}>
                      <TableCell>{u.username}</TableCell>
                      <TableCell>{u.employeeCode || '—'}</TableCell>
                      <TableCell>{u.group}</TableCell>
                      <TableCell>{u.reportingManager || '—'}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{u.temporaryPassword}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {created ? (
          <>
            <Button onClick={() => downloadText('cube-temporary-passwords.csv', passwordsCsv())}>Download passwords</Button>
            <Button variant="contained" onClick={onClose}>Done</Button>
          </>
        ) : (
          <>
            <Button onClick={onClose} disabled={busy}>Cancel</Button>
            <Button variant="contained" onClick={upload} disabled={!file || busy}>
              {busy ? 'Importing…' : 'Import'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  )
}
