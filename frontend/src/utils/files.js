import { MAX_FILE_BYTES } from '../config'
import { formatBytes } from './format'

/** Hands a downloaded blob to the browser as a file. */
export function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/bmp']
const TEXT_TYPES = ['text/plain', 'text/csv']

const OFFICE_TYPES = {
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'word',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'excel',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'powerpoint',
}
const OFFICE_EXTENSIONS = { docx: 'word', xlsx: 'excel', pptx: 'powerpoint' }

/**
 * How an attachment can be shown in the preview dialog:
 * 'image' | 'pdf' | 'text' | 'word' | 'excel' | 'powerpoint' | null (download only).
 * Office files are recognised by type or by extension (browsers often upload them as octet-stream).
 * Old binary formats (.doc, .xls, .ppt) cannot be previewed.
 */
export function previewKind(contentType, fileName) {
  const type = String(contentType || '').split(';')[0].trim().toLowerCase()
  if (IMAGE_TYPES.includes(type)) return 'image'
  if (type === 'application/pdf') return 'pdf'
  if (TEXT_TYPES.includes(type)) return 'text'
  if (OFFICE_TYPES[type]) return OFFICE_TYPES[type]
  const ext = String(fileName || '').split('.').pop().toLowerCase()
  return OFFICE_EXTENSIONS[ext] || null
}

/** Returns an error message for a file that cannot be uploaded, or null. */
export function fileProblem(file) {
  if (!file) return 'Invalid file'
  if (file.size === 0) return `"${file.name}" is empty`
  if (file.size > MAX_FILE_BYTES) {
    return `"${file.name}" is ${formatBytes(file.size)}. The limit is 10 MB per file.`
  }
  return null
}
