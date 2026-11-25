import { describe, expect, it } from 'vitest'
import { previewKind } from '../utils/files'

describe('previewKind', () => {
  it('previews Office files by type or by extension', () => {
    expect(previewKind('application/vnd.openxmlformats-officedocument.presentationml.presentation', 'deck.pptx')).toBe('powerpoint')
    expect(previewKind('application/octet-stream', 'Budget 2026.XLSX')).toBe('excel')
    expect(previewKind('', 'notes.docx')).toBe('word')
  })

  it('keeps pdf, images and text, and refuses old binary Office formats', () => {
    expect(previewKind('application/pdf', 'a.pdf')).toBe('pdf')
    expect(previewKind('image/png', 'a.png')).toBe('image')
    expect(previewKind('text/plain; charset=utf-8', 'a.txt')).toBe('text')
    expect(previewKind('application/vnd.ms-powerpoint', 'old.ppt')).toBeNull()
    expect(previewKind('application/zip', 'a.zip')).toBeNull()
  })
})
