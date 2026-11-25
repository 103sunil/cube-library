import { Link as RouterLink } from 'react-router-dom'
import { Alert, Avatar, Box, Card, CardContent, Chip, Link, Typography } from '@mui/material'
import AttachFileIcon from '@mui/icons-material/AttachFile'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import ScheduleIcon from '@mui/icons-material/Schedule'
import BookmarkButton from './BookmarkButton'
import { IconTile, KeywordChips, StatusChip } from './common'
import { formatDate, plural } from '../utils/format'

const TILE_GRADIENTS = [
  'linear-gradient(135deg, #6366F1, #8B5CF6)',
  'linear-gradient(135deg, #0EA5E9, #6366F1)',
  'linear-gradient(135deg, #14B8A6, #0EA5E9)',
  'linear-gradient(135deg, #F59E0B, #EF4444)',
  'linear-gradient(135deg, #EC4899, #8B5CF6)',
  'linear-gradient(135deg, #10B981, #14B8A6)',
]

function gradientFor(text) {
  let hash = 0
  for (const ch of String(text || '')) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return TILE_GRADIENTS[Math.abs(hash) % TILE_GRADIENTS.length]
}

function initials(name) {
  return (
    String(name || '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('') || '?'
  )
}

/** One knowledge item in a list. `actions` renders extra buttons on the right. */
export default function KnowledgeCard({ item, showStatus = false, showReason = false, actions, onBookmarkChange, onKeyword }) {
  const date = item.status === 'PUBLISHED' && item.publishedAt ? item.publishedAt : item.updatedAt
  const dateLabel = item.status === 'PUBLISHED' ? 'Published' : 'Updated'
  const author = item.ownerName || item.ownerUsername

  return (
    <Card
      sx={{
        bgcolor: '#FFFFFF',
        position: 'relative',
        '&:hover': {
          borderColor: '#C7D2FE',
          boxShadow: '0 22px 44px -24px rgba(79,70,229,0.45), 0 2px 6px rgba(16,24,40,0.05)',
          transform: 'translateY(-2px)',
        },
        '&:hover .cube-card-title': { color: 'primary.main' },
      }}
    >
      <CardContent sx={{ p: { xs: 2.25, md: 3 }, '&:last-child': { pb: { xs: 2.25, md: 3 } } }}>
        <Box sx={{ display: 'flex', gap: { xs: 1.75, md: 2.25 }, alignItems: 'flex-start' }}>
          <IconTile Icon={ArticleOutlinedIcon} size={46} gradient={gradientFor(item.title)} sx={{ display: { xs: 'none', sm: 'grid' } }} />

          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, justifyContent: 'space-between' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', minWidth: 0 }}>
                <Link
                  component={RouterLink}
                  to={`/knowledge/${item.knowledgeId}`}
                  underline="none"
                  className="cube-card-title"
                  sx={{
                    fontFamily: '"Plus Jakarta Sans", Inter, sans-serif',
                    fontSize: 18,
                    fontWeight: 700,
                    color: 'text.primary',
                    letterSpacing: '-0.01em',
                    lineHeight: 1.35,
                    overflowWrap: 'anywhere',
                    transition: 'color .2s ease',
                    // The whole card is clickable through the title link.
                    '&::after': { content: '""', position: 'absolute', inset: 0, borderRadius: 'inherit' },
                    '&:focus-visible': { outline: 'none' },
                    '&:focus-visible::after': { boxShadow: '0 0 0 3px rgba(79,70,229,0.35)' },
                  }}
                >
                  {item.title}
                </Link>
                {showStatus && <StatusChip status={item.status} />}
                {item.status === 'PENDING' && (
                  <Chip
                    size="small"
                    variant="outlined"
                    color={item.assignedToMe ? 'primary' : 'default'}
                    label={item.assignedToMe ? 'Assigned to you' : item.assignedReviewerName ? `Reviewer: ${item.assignedReviewerName}` : 'Unassigned'}
                  />
                )}
              </Box>
              {(actions || item.status === 'PUBLISHED') && (
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', position: 'relative', zIndex: 1, flexShrink: 0 }}>
                  {actions}
                  {item.status === 'PUBLISHED' && (
                    <BookmarkButton
                      key={String(item.bookmarked)}
                      knowledgeId={item.knowledgeId}
                      initial={item.bookmarked}
                      onChange={onBookmarkChange}
                      size="small"
                    />
                  )}
                </Box>
              )}
            </Box>

            {item.summary && (
              <Typography
                color="text.secondary"
                sx={{
                  mt: 0.75,
                  fontSize: 14.5,
                  lineHeight: 1.6,
                  overflowWrap: 'anywhere',
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                }}
              >
                {item.summary}
              </Typography>
            )}
            {item.keywords?.length > 0 && (
              <Box sx={{ mt: 1.5, position: 'relative', zIndex: onKeyword ? 1 : 'auto' }}>
                <KeywordChips keywords={item.keywords} onClick={onKeyword} />
              </Box>
            )}
            <Box sx={{ mt: 1.75, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', color: 'text.secondary' }}>
              <Avatar sx={{ width: 24, height: 24, fontSize: 11, bgcolor: '#EEF2FF', color: '#4338CA' }}>{initials(author)}</Avatar>
              <Typography variant="body2" sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
                <Box component="span" sx={{ fontWeight: 600, color: 'text.primary' }}>{author}</Box>
                <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                  <ScheduleIcon sx={{ fontSize: 15 }} />
                  {dateLabel} {formatDate(date)}
                </Box>
                <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                  <AttachFileIcon sx={{ fontSize: 15 }} />
                  {plural(item.attachmentCount || 0, 'attachment')}
                </Box>
              </Typography>
            </Box>
          </Box>
        </Box>
        {showReason && item.status === 'REJECTED' && item.rejectReason && (
          <Alert severity="error" sx={{ mt: 2 }}>
            <strong>Rejected:</strong> {item.rejectReason}
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}
