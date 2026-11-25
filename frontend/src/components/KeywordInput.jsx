import { useState } from 'react'
import { Box, Chip, TextField } from '@mui/material'
import { MAX_KEYWORD_LENGTH, MAX_KEYWORDS } from '../config'

/** Tag-style keyword entry: Enter or comma adds, Backspace on an empty box removes the last. */
export default function KeywordInput({ value, onChange, disabled, required = false, externalError = '' }) {
  const [text, setText] = useState('')
  const [error, setError] = useState('')

  const add = (raw) => {
    const parts = String(raw)
      .split(',')
      .map((part) => part.trim().replace(/\s+/g, ' '))
      .filter(Boolean)
    if (parts.length === 0) return
    const next = [...value]
    for (const part of parts) {
      if (part.length > MAX_KEYWORD_LENGTH) {
        setError(`Keywords can be at most ${MAX_KEYWORD_LENGTH} characters`)
        return
      }
      if (!next.some((k) => k.toLowerCase() === part.toLowerCase())) {
        next.push(part)
      }
    }
    if (next.length > MAX_KEYWORDS) {
      setError(`At most ${MAX_KEYWORDS} keywords`)
      return
    }
    setError('')
    setText('')
    onChange(next)
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      add(text)
    } else if (event.key === 'Backspace' && !text && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <Box>
      <TextField
        fullWidth
        label="Keywords"
        required={required}
        placeholder={value.length ? 'Add another keyword' : 'Type a keyword and press Enter'}
        value={text}
        disabled={disabled}
        onChange={(e) => {
          setText(e.target.value)
          if (error) setError('')
        }}
        onKeyDown={handleKeyDown}
        onBlur={() => text.trim() && add(text)}
        error={Boolean(error || (externalError && value.length === 0))}
        helperText={error || (value.length === 0 && externalError) || 'Keywords help people find this item. Press Enter after each one.'}
      />
      {value.length > 0 && (
        <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 1 }} aria-label="Selected keywords">
          {value.map((keyword) => (
            <Chip
              key={keyword}
              label={keyword}
              onDelete={disabled ? undefined : () => onChange(value.filter((k) => k !== keyword))}
              sx={{ bgcolor: '#EFF6FF', color: '#1D4ED8' }}
            />
          ))}
        </Box>
      )}
    </Box>
  )
}
