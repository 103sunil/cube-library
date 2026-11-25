import ApprovalsIcon from '@mui/icons-material/FactCheckOutlined'
import { useCallback, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { Alert, Box, Button, Tab, Tabs } from '@mui/material'
import { knowledgeApi } from '../api/cube'
import { useAsync } from '../utils/useAsync'
import KnowledgeCard from '../components/KnowledgeCard'
import { CardSkeletons, EmptyState, PageHeader, Pager } from '../components/common'
import { RejectDialog } from '../components/dialogs'
import { useNotify } from '../components/notify'

const TABS = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'PUBLISHED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export default function ApprovalsPage() {
  const notify = useNotify()
  const [params, setParams] = useSearchParams()
  const status = TABS.some((t) => t.value === params.get('status')) ? params.get('status') : 'PENDING'
  const page = Math.max(0, Number(params.get('page')) || 0)
  const loader = useCallback(() => knowledgeApi.byStatus(status, page), [status, page])
  const { data, loading, error, reload } = useAsync(loader)
  const [busyId, setBusyId] = useState(null)
  const [rejecting, setRejecting] = useState(null)

  const go = (nextStatus, nextPage = 0) => {
    const search = { status: nextStatus }
    if (nextPage) search.page = String(nextPage)
    setParams(search)
  }

  const act = async (item, action, message) => {
    setBusyId(item.knowledgeId)
    try {
      await action()
      notify(message)
      setRejecting(null)
      reload()
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusyId(null)
    }
  }

  const rowActions = (item) => {
    const busy = busyId === item.knowledgeId
    const review = (
      <Button size="small" variant="outlined" component={RouterLink} to={`/knowledge/${item.knowledgeId}`}>
        Review
      </Button>
    )
    if (item.status === 'PENDING') {
      return (
        <>
          {review}
          <Button
            size="small"
            variant="contained"
            color="success"
            disabled={busy}
            onClick={() => act(item, () => knowledgeApi.approve(item.knowledgeId), `Approved “${item.title}”`)}
          >
            Approve
          </Button>
          <Button size="small" variant="outlined" color="error" disabled={busy} onClick={() => setRejecting(item)}>
            Reject
          </Button>
        </>
      )
    }
    if (item.status === 'PUBLISHED') {
      return (
        <>
          {review}
          <Button size="small" disabled={busy} onClick={() => act(item, () => knowledgeApi.archive(item.knowledgeId), 'Archived')}>
            Archive
          </Button>
        </>
      )
    }
    if (item.status === 'ARCHIVED') {
      return (
        <>
          {review}
          <Button size="small" disabled={busy} onClick={() => act(item, () => knowledgeApi.restore(item.knowledgeId), 'Restored')}>
            Restore
          </Button>
        </>
      )
    }
    return review
  }

  return (
    <Box>
      <PageHeader title="Approvals" icon={ApprovalsIcon} subtitle="Review submissions before they become visible to everyone" />
      <Tabs value={status} onChange={(_, value) => go(value)} variant="scrollable" sx={{ mb: 3 }}>
        {TABS.map((tab) => (
          <Tab key={tab.value} value={tab.value} label={tab.label} />
        ))}
      </Tabs>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CardSkeletons />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={status === 'PENDING' ? 'All caught up' : 'Nothing here'}
          message={status === 'PENDING' ? 'There are no submissions waiting for review.' : undefined}
        />
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, opacity: loading ? 0.6 : 1 }}>
        {data?.content.map((item) => (
          <KnowledgeCard key={item.knowledgeId} item={item} showStatus showReason actions={rowActions(item)} />
        ))}
      </Box>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={(p) => go(status, p)} />}

      <RejectDialog
        open={Boolean(rejecting)}
        itemTitle={rejecting?.title}
        busy={Boolean(busyId)}
        onClose={() => setRejecting(null)}
        onSubmit={(reason) =>
          act(rejecting, () => knowledgeApi.reject(rejecting.knowledgeId, reason), `Rejected “${rejecting.title}”`)
        }
      />
    </Box>
  )
}
