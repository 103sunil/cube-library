import { useCallback, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material'
import { adminApi } from '../../api/cube'
import { useAsync } from '../../utils/useAsync'
import { formatDateTime } from '../../utils/format'
import { Pager } from '../../components/common'

export default function AuditTab() {
  const [actor, setActor] = useState('')
  const [search, setSearch] = useState({ actor: '', page: 0 })
  const loader = useCallback(() => adminApi.audit(search.actor, search.page), [search])
  const { data, loading, error } = useAsync(loader)

  return (
    <Box>
      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault()
          setSearch({ actor: actor.trim(), page: 0 })
        }}
        sx={{ display: 'flex', gap: 1.5, mb: 2 }}
      >
        <TextField size="small" placeholder="Filter by username" value={actor} onChange={(e) => setActor(e.target.value)} sx={{ flex: '1 1 260px' }} />
        <Button type="submit" variant="outlined">Filter</Button>
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CircularProgress aria-label="Loading" />}
      {data && (
        <TableContainer component={Paper} variant="outlined" sx={{ bgcolor: '#FFFFFF' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Who</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Item</TableCell>
                <TableCell>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.content.map((entry) => (
                <TableRow key={entry.auditId}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(entry.createdAt)}</TableCell>
                  <TableCell>{entry.actor || '—'}</TableCell>
                  <TableCell>{entry.action}</TableCell>
                  <TableCell>{[entry.entityType, entry.entityId].filter(Boolean).join(' #')}</TableCell>
                  <TableCell sx={{ overflowWrap: 'anywhere' }}>{entry.details}</TableCell>
                </TableRow>
              ))}
              {data.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} align="center">No entries</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={(p) => setSearch({ ...search, page: p })} />}
    </Box>
  )
}
