import { Box, Card, CardContent, Chip, Pagination, Paper, Skeleton, Typography } from '@mui/material'
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined'
import { BRAND } from '../theme'

const STATUS_STYLE = {
  DRAFT: { label: 'Draft', color: '#475569', bg: '#F1F5F9', dot: '#94A3B8' },
  PENDING: { label: 'Pending', color: '#B45309', bg: '#FEF3C7', dot: '#F59E0B' },
  PUBLISHED: { label: 'Published', color: '#047857', bg: '#D1FAE5', dot: '#10B981' },
  REJECTED: { label: 'Rejected', color: '#BE123C', bg: '#FFE4E6', dot: '#F43F5E' },
  ARCHIVED: { label: 'Archived', color: '#475569', bg: '#E2E8F0', dot: '#64748B' },
}

export function StatusChip({ status, size = 'small' }) {
  const style = STATUS_STYLE[status] || { label: status, color: '#475569', bg: '#F1F5F9', dot: '#94A3B8' }
  return (
    <Chip
      size={size}
      label={style.label}
      icon={<Box component="span" sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: style.dot, ml: '8px !important' }} />}
      sx={{ color: style.color, bgcolor: style.bg, fontWeight: 700, letterSpacing: 0.2, border: 0 }}
    />
  )
}

/** Gradient tile holding an icon - used in page headers and cards. */
export function IconTile({ Icon, size = 48, gradient = BRAND.gradient, sx }) {
  return (
    <Box
      aria-hidden
      sx={{
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: size / 3.4 + 'px',
        display: 'grid',
        placeItems: 'center',
        color: '#FFFFFF',
        background: gradient,
        boxShadow: '0 10px 24px -12px rgba(79,70,229,0.7)',
        ...sx,
      }}
    >
      <Icon sx={{ fontSize: size * 0.5 }} />
    </Box>
  )
}

export function PageHeader({ title, subtitle, actions, icon }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: { xs: 'flex-start', sm: 'center' },
        justifyContent: 'space-between',
        flexDirection: { xs: 'column', sm: 'row' },
        gap: 2,
        mb: 4,
      }}
    >
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', minWidth: 0 }}>
        {icon && <IconTile Icon={icon} size={52} />}
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h4" component="h1" sx={{ fontSize: { xs: 26, md: 32 } }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography color="text.secondary" sx={{ mt: 0.5, fontSize: 15.5, maxWidth: 720 }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', flexShrink: 0 }}>{actions}</Box>}
    </Box>
  )
}

export function EmptyState({ title, message, icon: Icon = InboxOutlinedIcon, action }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: { xs: 4, md: 7 },
        textAlign: 'center',
        bgcolor: 'rgba(255,255,255,0.75)',
        borderStyle: 'dashed',
        boxShadow: 'none',
        borderRadius: 5,
      }}
    >
      <Box sx={{ position: 'relative', width: 96, height: 96, mx: 'auto', mb: 2.5 }}>
        <Box sx={{ position: 'absolute', inset: 0, borderRadius: '50%', background: 'linear-gradient(135deg, rgba(79,70,229,0.12), rgba(168,85,247,0.12))' }} />
        <Box sx={{ position: 'absolute', inset: 14, borderRadius: '50%', bgcolor: '#FFFFFF', boxShadow: '0 8px 20px -10px rgba(79,70,229,0.5)', display: 'grid', placeItems: 'center' }}>
          <Icon sx={{ fontSize: 32, color: 'primary.main' }} />
        </Box>
      </Box>
      <Typography variant="h6">{title}</Typography>
      {message && <Typography color="text.secondary" sx={{ mt: 0.75, maxWidth: 440, mx: 'auto' }}>{message}</Typography>}
      {action && <Box sx={{ mt: 3 }}>{action}</Box>}
    </Paper>
  )
}

/** Placeholder cards while a list loads. */
export function CardSkeletons({ count = 3 }) {
  return (
    <Box role="progressbar" aria-label="Loading" sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {Array.from({ length: count }).map((_, i) => (
        <Card key={i}>
          <CardContent sx={{ p: 3, display: 'flex', gap: 2 }}>
            <Skeleton variant="rounded" width={48} height={48} sx={{ borderRadius: 3, flexShrink: 0 }} />
            <Box sx={{ flex: 1 }}>
              <Skeleton width="45%" height={28} />
              <Skeleton width="90%" />
              <Skeleton width="70%" />
              <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                <Skeleton variant="rounded" width={64} height={22} />
                <Skeleton variant="rounded" width={80} height={22} />
              </Box>
            </Box>
          </CardContent>
        </Card>
      ))}
    </Box>
  )
}

export function Pager({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
      <Pagination
        page={page + 1}
        count={totalPages}
        color="primary"
        shape="rounded"
        onChange={(_, value) => onChange(value - 1)}
      />
    </Box>
  )
}

export function KeywordChips({ keywords, onClick }) {
  if (!keywords || keywords.length === 0) return null
  return (
    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
      {keywords.map((keyword) => (
        <Chip
          key={keyword}
          label={`#${keyword}`}
          size="small"
          onClick={onClick ? () => onClick(keyword) : undefined}
          sx={{
            bgcolor: '#EEF2FF',
            color: '#4338CA',
            fontWeight: 600,
            border: 0,
            '&:hover': onClick ? { bgcolor: '#E0E7FF' } : undefined,
          }}
        />
      ))}
    </Box>
  )
}
