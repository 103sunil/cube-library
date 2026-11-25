import { useState } from 'react'
import { IconButton, Tooltip } from '@mui/material'
import BookmarkIcon from '@mui/icons-material/Bookmark'
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder'
import { knowledgeApi } from '../api/cube'
import { useNotify } from './notify'

/** Saves / un-saves a published item for the current user. */
export default function BookmarkButton({ knowledgeId, initial, onChange, size = 'medium' }) {
  const notify = useNotify()
  const [saved, setSaved] = useState(Boolean(initial))
  const [busy, setBusy] = useState(false)

  const toggle = async (event) => {
    event.preventDefault()
    event.stopPropagation()
    setBusy(true)
    try {
      if (saved) await knowledgeApi.removeBookmark(knowledgeId)
      else await knowledgeApi.bookmark(knowledgeId)
      setSaved(!saved)
      notify(saved ? 'Removed from bookmarks' : 'Bookmarked')
      onChange?.(!saved)
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const label = saved ? 'Remove bookmark' : 'Bookmark'
  return (
    <Tooltip title={label}>
      <span>
        <IconButton aria-label={label} aria-pressed={saved} onClick={toggle} disabled={busy} size={size} color={saved ? 'primary' : 'default'}>
          {saved ? <BookmarkIcon /> : <BookmarkBorderIcon />}
        </IconButton>
      </span>
    </Tooltip>
  )
}
