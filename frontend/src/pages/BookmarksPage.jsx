import BookmarksIcon from '@mui/icons-material/BookmarksOutlined'
import { useCallback, useState } from 'react'
import { Alert, Box } from '@mui/material'
import { knowledgeApi } from '../api/cube'
import { useAsync } from '../utils/useAsync'
import KnowledgeCard from '../components/KnowledgeCard'
import { CardSkeletons, EmptyState, PageHeader, Pager } from '../components/common'

export default function BookmarksPage() {
  const [page, setPage] = useState(0)
  const loader = useCallback(() => knowledgeApi.bookmarks(page), [page])
  const { data, loading, error, reload } = useAsync(loader)

  return (
    <Box>
      <PageHeader title="Bookmarks" icon={BookmarksIcon} subtitle="Knowledge you saved for later" />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && !data && <CardSkeletons />}
      {data && data.content.length === 0 && (
        <EmptyState title="No bookmarks yet" message="Use the bookmark icon on any published item to save it here." />
      )}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, opacity: loading ? 0.6 : 1 }}>
        {data?.content.map((item) => (
          <KnowledgeCard key={item.knowledgeId} item={item} onBookmarkChange={(saved) => !saved && reload()} />
        ))}
      </Box>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </Box>
  )
}
