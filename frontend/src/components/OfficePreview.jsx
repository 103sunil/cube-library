import { useEffect, useRef, useState } from 'react'
import { Alert, Box, CircularProgress, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, Typography } from '@mui/material'

const MAX_ROWS = 500
const MAX_COLS = 60

/** Neutralises links inside rendered documents: no javascript: URLs, external links open in a new tab. */
function secureLinks(root) {
  root.querySelectorAll('a[href]').forEach((a) => {
    const href = (a.getAttribute('href') || '').trim().toLowerCase()
    if (href.startsWith('javascript:') || href.startsWith('data:') || href.startsWith('vbscript:')) {
      a.removeAttribute('href')
    } else if (!href.startsWith('#')) {
      a.setAttribute('target', '_blank')
      a.setAttribute('rel', 'noopener noreferrer')
    }
  })
  root.querySelectorAll('script, iframe, object, embed').forEach((el) => el.remove())
}

function Loading({ label }) {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: 300, width: '100%' }}>
      <CircularProgress aria-label={label} />
    </Box>
  )
}

function useRender(render, deps) {
  const [state, setState] = useState({ loading: true, error: '' })
  useEffect(() => {
    let cancelled = false
    let cleanup
    render(() => cancelled)
      .then((done) => {
        cleanup = done
        if (!cancelled) setState({ loading: false, error: '' })
      })
      .catch((err) => {
        if (!cancelled) setState({ loading: false, error: err?.message || 'This file could not be displayed.' })
      })
    return () => {
      cancelled = true
      if (typeof cleanup === 'function') cleanup()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return state
}

/** Word (.docx): rendered page by page. */
export function WordPreview({ blob }) {
  const body = useRef(null)
  const styles = useRef(null)
  const state = useRender(async () => {
    const { renderAsync } = await import('docx-preview')
    body.current.innerHTML = ''
    styles.current.innerHTML = ''
    await renderAsync(blob, body.current, styles.current, {
      inWrapper: true,
      breakPages: true,
      ignoreLastRenderedPageBreak: true,
      renderAltChunks: false,
      renderComments: false,
      useBase64URL: true,
    })
    secureLinks(body.current)
  }, [blob])

  return (
    <Box sx={{ width: '100%' }}>
      {state.loading && <Loading label="Loading document" />}
      {state.error && <Alert severity="warning">{state.error} Download the file to open it in Word.</Alert>}
      <div ref={styles} />
      <Box
        ref={body}
        data-testid="word-preview"
        sx={{
          '& .docx-wrapper': { bgcolor: '#EEF0F6', p: { xs: 1, md: 3 }, borderRadius: 2 },
          '& .docx-wrapper > section.docx': { boxShadow: '0 8px 24px -12px rgba(15,23,42,0.35)', mb: 3, maxWidth: '100%' },
        }}
      />
    </Box>
  )
}

/**
 * Some tools (e.g. pptxgenjs exports) list parts in [Content_Types].xml that are not in the file.
 * PowerPoint ignores those entries but pptx-preview stops; remove them before rendering.
 */
async function repairPptx(data) {
  const { default: JSZip } = await import('jszip')
  const zip = await JSZip.loadAsync(data)
  const typesFile = zip.file('[Content_Types].xml')
  if (!typesFile) return data
  const types = await typesFile.async('text')
  let changed = false
  const fixed = types.replace(/<Override\b[^>]*PartName="\/([^"]+)"[^>]*\/>/g, (entry, part) => {
    if (zip.file(part)) return entry
    changed = true
    return ''
  })
  if (!changed) return data
  zip.file('[Content_Types].xml', fixed)
  return zip.generateAsync({ type: 'arraybuffer' })
}

// pptx-preview shares global state between viewers: starting one tells every other one to stop.
// So decks are rendered strictly one after another, and a viewer that was closed meanwhile is skipped.
let pptxQueue = Promise.resolve()
function oneAtATime(task) {
  const run = pptxQueue.then(task, task)
  pptxQueue = run.catch(() => {})
  return run
}

/** PowerPoint (.pptx): all slides one below the other. */
export function PowerPointPreview({ blob }) {
  const host = useRef(null)
  const state = useRender(
    (isCancelled) =>
      oneAtATime(async () => {
        if (isCancelled() || !host.current) return undefined
        const { init } = await import('pptx-preview')
        const data = await repairPptx(await blob.arrayBuffer())
        if (isCancelled() || !host.current) return undefined
        const width = Math.min(960, Math.max(320, (host.current.clientWidth || 960) - 8))
        host.current.innerHTML = ''
        const previewer = init(host.current, { width, height: Math.round((width * 9) / 16), mode: 'list' })
        const pptx = await previewer.preview(data)
        if (!pptx?.slides?.length) {
          throw new Error('This presentation could not be displayed.')
        }
        secureLinks(host.current)
        return () => previewer.destroy?.()
      }),
    [blob]
  )

  return (
    <Box sx={{ width: '100%' }}>
      {state.loading && <Loading label="Loading slides" />}
      {state.error && <Alert severity="warning">{state.error} Download the file to open it in PowerPoint.</Alert>}
      <Box
        ref={host}
        data-testid="pptx-preview"
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          bgcolor: '#EEF0F6',
          borderRadius: 2,
          p: { xs: 1, md: 2 },
          '& > div': { maxWidth: '100%' },
          '& .pptx-preview-slide-wrapper': { boxShadow: '0 8px 24px -12px rgba(15,23,42,0.35)', mb: 2 },
        }}
      />
    </Box>
  )
}

function columnName(index) {
  let name = ''
  let n = index + 1
  while (n > 0) {
    const r = (n - 1) % 26
    name = String.fromCharCode(65 + r) + name
    n = Math.floor((n - 1) / 26)
  }
  return name
}

function cellText(value) {
  if (value === null || value === undefined) return ''
  if (value instanceof Date) return value.toLocaleDateString()
  return String(value)
}

/** Excel (.xlsx): one tab per sheet, first rows as a grid. */
export function ExcelPreview({ blob }) {
  const [sheets, setSheets] = useState([])
  const [active, setActive] = useState(0)
  const state = useRender(async () => {
    const { default: readXlsxFile } = await import('read-excel-file/browser')
    const result = await readXlsxFile(blob)
    setSheets(result || [])
    setActive(0)
  }, [blob])

  const sheet = sheets[active]
  const rows = sheet?.data || []
  const width = Math.min(MAX_COLS, rows.reduce((max, row) => Math.max(max, row.length), 0))

  return (
    <Box sx={{ width: '100%' }}>
      {state.loading && <Loading label="Loading spreadsheet" />}
      {state.error && <Alert severity="warning">{state.error} Download the file to open it in Excel.</Alert>}
      {!state.loading && !state.error && sheets.length === 0 && <Alert severity="info">This workbook has no data.</Alert>}
      {sheets.length > 1 && (
        <Tabs value={active} onChange={(_, v) => setActive(v)} variant="scrollable" sx={{ mb: 1.5 }}>
          {sheets.map((s, i) => (
            <Tab key={s.sheet + i} value={i} label={s.sheet} />
          ))}
        </Tabs>
      )}
      {sheet && (
        <>
          <TableContainer data-testid="xlsx-preview" sx={{ maxHeight: '65vh', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            <Table size="small" stickyHeader sx={{ '& td, & th': { borderRight: '1px solid #EEF0F6', whiteSpace: 'nowrap', maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' } }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 48, textAlign: 'center' }} />
                  {Array.from({ length: width }).map((_, c) => (
                    <TableCell key={c} sx={{ textAlign: 'center' }}>{columnName(c)}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.slice(0, MAX_ROWS).map((row, r) => (
                  <TableRow key={r} hover>
                    <TableCell sx={{ color: 'text.secondary', bgcolor: '#F8F9FC', textAlign: 'center', fontSize: 12 }}>{r + 1}</TableCell>
                    {Array.from({ length: width }).map((_, c) => (
                      <TableCell key={c} title={cellText(row[c])}>{cellText(row[c])}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {(rows.length > MAX_ROWS || width === MAX_COLS) && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Showing the first {Math.min(rows.length, MAX_ROWS)} rows and {width} columns. Download the file to see everything.
            </Typography>
          )}
        </>
      )}
    </Box>
  )
}
