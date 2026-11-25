import { useRef, useState } from 'react'
import { Alert, Box, IconButton, List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material'
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined'
import CloseIcon from '@mui/icons-material/Close'
import { MAX_FILES_PER_UPLOAD } from '../config'
import { fileProblem } from '../utils/files'
import { formatBytes } from '../utils/format'

/**
 * Stages files for upload (nothing is sent until the form is saved).
 * Files over 10 MB or empty files are refused immediately.
 */
export default function FileDropZone({ files, onChange, disabled, existingCount = 0 }) {
  const inputRef = useRef(null)
  const [dragging, setDragging] = useState(false)
  const [problems, setProblems] = useState([])

  const addFiles = (incoming) => {
    const accepted = []
    const issues = []
    for (const file of incoming) {
      const problem = fileProblem(file)
      if (problem) {
        issues.push(problem)
      } else if (!files.some((f) => f.name === file.name && f.size === file.size)) {
        accepted.push(file)
      }
    }
    const room = MAX_FILES_PER_UPLOAD - files.length
    if (accepted.length > room) {
      issues.push(`You can attach at most ${MAX_FILES_PER_UPLOAD} files at a time.`)
      accepted.splice(room)
    }
    setProblems(issues)
    if (accepted.length) onChange([...files, ...accepted])
  }

  const onDrop = (event) => {
    event.preventDefault()
    setDragging(false)
    if (!disabled) addFiles(Array.from(event.dataTransfer.files || []))
  }

  return (
    <Box>
      <Box
        role="button"
        tabIndex={0}
        aria-label="Add attachments"
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && !disabled && inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        sx={{
          border: '2px dashed',
          borderColor: dragging ? 'primary.main' : '#CBD5E1',
          bgcolor: dragging ? '#EFF6FF' : '#F8FAFC',
          borderRadius: 2,
          p: 3,
          textAlign: 'center',
          cursor: disabled ? 'default' : 'pointer',
          transition: 'all .15s',
        }}
      >
        <CloudUploadOutlinedIcon color="primary" sx={{ fontSize: 36 }} />
        <Typography sx={{ fontWeight: 600 }}>Drag & drop files here, or click to browse</Typography>
        <Typography variant="body2" color="text.secondary">
          Any file type · up to 10 MB per file
          {existingCount > 0 ? ` · ${existingCount} already attached` : ''}
        </Typography>
        <input
          ref={inputRef}
          type="file"
          multiple
          hidden
          data-testid="file-input"
          onChange={(e) => {
            addFiles(Array.from(e.target.files || []))
            e.target.value = ''
          }}
        />
      </Box>

      {problems.map((problem) => (
        <Alert key={problem} severity="warning" sx={{ mt: 1 }}>
          {problem}
        </Alert>
      ))}

      {files.length > 0 && (
        <List dense sx={{ mt: 1 }}>
          {files.map((file) => (
            <ListItem
              key={`${file.name}-${file.size}`}
              sx={{ border: '1px solid #E5E7EB', borderRadius: 1.5, mb: 1, bgcolor: '#FFFFFF' }}
              secondaryAction={
                <IconButton
                  edge="end"
                  aria-label={`Remove ${file.name}`}
                  disabled={disabled}
                  onClick={() => onChange(files.filter((f) => f !== file))}
                >
                  <CloseIcon />
                </IconButton>
              }
            >
              <ListItemIcon sx={{ minWidth: 36 }}>
                <InsertDriveFileOutlinedIcon color="action" />
              </ListItemIcon>
              <ListItemText primary={file.name} secondary={formatBytes(file.size)} />
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  )
}
