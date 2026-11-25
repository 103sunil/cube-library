import UploadsIcon from '@mui/icons-material/FolderOutlined'
import { useCallback } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { Alert, Box, Button, Tab, Tabs } from '@mui/material'
import { knowledgeApi } from '../api/cube'
import { useAuth } from '../auth/AuthContext'
import { PERM } from '../config'
import { useAsync } from '../utils/useAsync'
import KnowledgeCard from '../components/KnowledgeCard'
import { CardSkeletons, EmptyState, PageHeader, Pager } from '../components/common'

const TABS = [
  { value: '', label: 'All' },
  { value: 'DRAFT', label: 'Drafts' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export default function MyUploadsPage() {
  // Approvers publish directly, so they never have Pending or Rejected items.
  const isApprover = useAuth().can(PERM.APPROVE)
  const tabs = isApprover ? TABS.filter((t) => t.value !== 'PENDING' && t.value !== 'REJECTED') : TABS
  const [params, setParams] = useSearchParams()
  const requested = params.get('status') || ''
  const status = tabs.some((t) => t.value === requested) ? requested : ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const loader = useCallback(() => knowledgeApi.mine(status, page), [status, page])
  const { data, loading, error } = useAsync(loader)

  const go = (nextStatus, nextPage = 0) => {
    const search = {}
    if (nextStatus) search.status = nextStatus
    if (nextPage) search.page = String(nextPage)
    setParams(search)
  }

  return (
    <Box>
      <PageHeader icon={UploadsIcon}
        title="My uploads"
        subtitle="Everything you have submitted, with its current status"
        actions={
          <Button variant="contained" component={RouterLink} to="/submit">
            New submission
          </Button>
        }
      />
      <Tabs value={status} onChange={(_, value) => go(value)} variant="scrollable" sx={{ mb: 3 }}>
        {tabs.map((tab) => (
          <Tab key={tab.value || 'all'} value={tab.value} label={tab.label} />
        ))}
      </Tabs>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CardSkeletons />}
      {data && data.content.length === 0 && (
        <EmptyState title="Nothing here yet" message="Items you submit will appear here." />
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, opacity: loading ? 0.6 : 1 }}>
        {data?.content.map((item) => (
          <KnowledgeCard
            key={item.knowledgeId}
            item={item}
            showStatus
            showReason
            actions={
              (item.status === 'DRAFT' || item.status === 'REJECTED') && (
                <Button variant="outlined" size="small" component={RouterLink} to={`/knowledge/${item.knowledgeId}/edit`}>
                  {item.status === 'REJECTED' ? 'Edit & resubmit' : 'Continue editing'}
                </Button>
              )
            }
          />
        ))}
      </Box>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={(p) => go(status, p)} />}
    </Box>
  )
}
