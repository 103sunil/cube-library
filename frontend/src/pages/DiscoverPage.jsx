import { useCallback, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Box, Button, Chip, InputAdornment, Paper, TextField, Typography } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import CloseIcon from '@mui/icons-material/Close'
import TravelExploreOutlinedIcon from '@mui/icons-material/TravelExploreOutlined'
import { knowledgeApi } from '../api/cube'
import { useAuth } from '../auth/AuthContext'
import { PERM } from '../config'
import { BRAND } from '../theme'
import { useAsync } from '../utils/useAsync'
import KnowledgeCard from '../components/KnowledgeCard'
import { CardSkeletons, EmptyState, Pager } from '../components/common'

function greeting() {
  const h = new Date().getHours()
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  return 'Good evening'
}

function SearchForm({ initial, onSearch }) {
  const [text, setText] = useState(initial)
  return (
    <Paper
      component="form"
      role="search"
      onSubmit={(e) => {
        e.preventDefault()
        onSearch(text.trim())
      }}
      sx={{
        p: 0.75,
        display: 'flex',
        gap: 1,
        bgcolor: 'rgba(255,255,255,0.97)',
        borderRadius: 4,
        boxShadow: '0 24px 48px -20px rgba(15,23,42,0.55)',
        maxWidth: 760,
      }}
    >
      <TextField
        fullWidth
        placeholder="Search titles, descriptions and keywords"
        sx={{
          '& .MuiOutlinedInput-root': { bgcolor: 'transparent', boxShadow: 'none !important', fontSize: 16 },
          '& .MuiOutlinedInput-notchedOutline': { border: 0 },
        }}
        value={text}
        onChange={(e) => setText(e.target.value)}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ color: 'primary.main' }} />
              </InputAdornment>
            ),
          },
          htmlInput: { 'aria-label': 'Search knowledge' },
        }}
      />
      <Button type="submit" variant="contained" size="large" sx={{ px: { xs: 2.5, sm: 4 }, borderRadius: 3 }}>
        Search
      </Button>
    </Paper>
  )
}

/** Most used keywords on the current page, for one-click searches. */
function topKeywords(items, limit = 6) {
  const counts = new Map()
  items.forEach((item) => (item.keywords || []).forEach((k) => counts.set(k, (counts.get(k) || 0) + 1)))
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, limit).map(([k]) => k)
}

export default function DiscoverPage() {
  const [params, setParams] = useSearchParams()
  const auth = useAuth()
  const canSearch = auth.can(PERM.SEARCH)
  const firstName = auth.user?.firstName || auth.user?.displayName?.split(' ')[0]
  const q = canSearch ? params.get('q') || '' : ''
  const page = Math.max(0, Number(params.get('page')) || 0)

  const loader = useCallback(() => (q ? knowledgeApi.search(q, page) : knowledgeApi.published(page)), [q, page])
  const { data, loading, error } = useAsync(loader)
  const trending = useMemo(() => (data && !q ? topKeywords(data.content) : []), [data, q])

  const go = (next) => {
    const search = {}
    if (next.q) search.q = next.q
    if (next.page) search.page = String(next.page)
    setParams(search)
  }
  const searchKeyword = canSearch ? (k) => go({ q: k, page: 0 }) : undefined

  return (
    <Box>
      <Box
        sx={{
          mb: 4,
          px: { xs: 3, md: 6 },
          py: { xs: 4, md: 6 },
          borderRadius: 6,
          color: '#FFFFFF',
          position: 'relative',
          overflow: 'hidden',
          background: BRAND.heroGradient,
          boxShadow: '0 30px 60px -30px rgba(49,46,129,0.75)',
        }}
      >
        {/* subtle grid + glow decoration */}
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            inset: 0,
            opacity: 0.18,
            backgroundImage:
              'linear-gradient(rgba(255,255,255,0.35) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.35) 1px, transparent 1px)',
            backgroundSize: '44px 44px',
            maskImage: 'radial-gradient(ellipse at 70% 30%, black 10%, transparent 70%)',
            WebkitMaskImage: 'radial-gradient(ellipse at 70% 30%, black 10%, transparent 70%)',
          }}
        />
        <Box aria-hidden sx={{ position: 'absolute', right: -80, top: -80, width: 300, height: 300, borderRadius: '50%', background: 'radial-gradient(circle, rgba(255,255,255,0.22), transparent 70%)' }} />
        <Box sx={{ position: 'relative' }}>
          <Chip
            icon={<AutoAwesomeIcon sx={{ fontSize: '16px !important', color: '#FDE68A !important' }} />}
            label={firstName ? `${greeting()}, ${firstName}` : greeting()}
            sx={{ mb: 2, color: '#FFFFFF', bgcolor: 'rgba(255,255,255,0.14)', border: '1px solid rgba(255,255,255,0.22)', backdropFilter: 'blur(6px)' }}
          />
          <Typography variant="h3" component="h1" sx={{ color: '#FFFFFF', fontSize: { xs: 34, md: 46 }, lineHeight: 1.1 }}>
            Discover
          </Typography>
          <Typography sx={{ opacity: 0.85, mt: 1.25, mb: canSearch ? 3.5 : 0, maxWidth: 580, fontSize: { xs: 15, md: 17 } }}>
            Find answers, guides and documents published across the organisation.
          </Typography>
          {canSearch && <SearchForm key={q} initial={q} onSearch={(text) => go({ q: text, page: 0 })} />}
          {canSearch && trending.length > 0 && (
            <Box sx={{ mt: 2.25, display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
              <Typography variant="body2" sx={{ opacity: 0.75, mr: 0.5 }}>Popular:</Typography>
              {trending.map((k) => (
                <Chip
                  key={k}
                  label={k}
                  size="small"
                  onClick={() => searchKeyword(k)}
                  sx={{
                    color: '#FFFFFF',
                    bgcolor: 'rgba(255,255,255,0.12)',
                    border: '1px solid rgba(255,255,255,0.2)',
                    '&:hover': { bgcolor: 'rgba(255,255,255,0.24)' },
                  }}
                />
              ))}
            </Box>
          )}
        </Box>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {data && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2, gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="h6" component="h2" sx={{ fontSize: 17 }}>
            {q ? `${data.totalElements} result${data.totalElements === 1 ? '' : 's'} for “${q}”` : `${data.totalElements} published item${data.totalElements === 1 ? '' : 's'}`}
          </Typography>
          {q && (
            <Button size="small" startIcon={<CloseIcon />} onClick={() => go({})}>
              Clear search
            </Button>
          )}
        </Box>
      )}

      {loading && !data && <CardSkeletons />}

      {data && data.content.length === 0 && (
        <EmptyState
          icon={TravelExploreOutlinedIcon}
          title={q ? 'No results' : 'Nothing published yet'}
          message={q ? 'Try different or fewer words, or one of the popular keywords.' : 'Approved knowledge will appear here.'}
        />
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, opacity: loading ? 0.55 : 1, transition: 'opacity .2s ease' }}>
        {data?.content.map((item, i) => (
          <Box key={item.knowledgeId} sx={{ animation: 'cubeFadeUp .35s ease both', animationDelay: `${Math.min(i, 8) * 35}ms` }}>
            <KnowledgeCard item={item} onKeyword={searchKeyword} />
          </Box>
        ))}
      </Box>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={(p) => go({ q, page: p })} />}
    </Box>
  )
}
