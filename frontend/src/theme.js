import { alpha, createTheme } from '@mui/material/styles'

// Design tokens -------------------------------------------------------------
export const BRAND = {
  primary: '#4F46E5', // indigo
  primaryDark: '#4338CA',
  violet: '#8B5CF6',
  cyan: '#06B6D4',
  ink: '#0B1020',
  muted: '#5B6478',
  line: '#E6E8F0',
  surface: '#FFFFFF',
  canvas: '#F6F7FB',
  gradient: 'linear-gradient(135deg, #4F46E5 0%, #7C3AED 55%, #A855F7 100%)',
  heroGradient:
    'radial-gradient(900px 400px at 10% 0%, rgba(99,102,241,0.55), transparent 60%), radial-gradient(700px 380px at 90% 10%, rgba(168,85,247,0.55), transparent 60%), radial-gradient(600px 300px at 60% 120%, rgba(6,182,212,0.35), transparent 60%), linear-gradient(135deg, #1E1B4B 0%, #312E81 45%, #4C1D95 100%)',
}

const HEADING_FONT = ['"Plus Jakarta Sans"', 'Inter', '"Segoe UI"', 'Roboto', 'Arial', 'sans-serif'].join(',')
const BODY_FONT = ['Inter', '"Segoe UI"', 'Roboto', 'Arial', 'sans-serif'].join(',')

const SOFT_SHADOW = '0 1px 2px rgba(16,24,40,0.04), 0 1px 3px rgba(16,24,40,0.06)'
const LIFT_SHADOW = '0 18px 40px -20px rgba(79,70,229,0.45), 0 2px 6px rgba(16,24,40,0.06)'
const FOCUS_RING = `0 0 0 4px ${alpha(BRAND.primary, 0.14)}`

const theme = createTheme({
  palette: {
    primary: { main: BRAND.primary, dark: BRAND.primaryDark, light: '#818CF8' },
    secondary: { main: BRAND.violet },
    info: { main: BRAND.cyan },
    success: { main: '#16A34A' },
    error: { main: '#E11D48' },
    warning: { main: '#D97706' },
    background: { default: BRAND.canvas, paper: BRAND.surface },
    text: { primary: BRAND.ink, secondary: BRAND.muted },
    divider: BRAND.line,
    action: { hover: alpha(BRAND.primary, 0.05), selected: alpha(BRAND.primary, 0.08) },
  },
  typography: {
    fontFamily: BODY_FONT,
    h1: { fontFamily: HEADING_FONT, fontWeight: 800 },
    h2: { fontFamily: HEADING_FONT, fontWeight: 800 },
    h3: { fontFamily: HEADING_FONT, fontWeight: 800, letterSpacing: '-0.03em' },
    h4: { fontFamily: HEADING_FONT, fontWeight: 800, letterSpacing: '-0.025em' },
    h5: { fontFamily: HEADING_FONT, fontWeight: 700, letterSpacing: '-0.015em' },
    h6: { fontFamily: HEADING_FONT, fontWeight: 700, letterSpacing: '-0.01em' },
    subtitle1: { fontWeight: 600 },
    button: { fontWeight: 600, letterSpacing: 0 },
    overline: { fontWeight: 700, letterSpacing: '0.12em' },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: BRAND.canvas,
          backgroundImage:
            'radial-gradient(1100px 420px at -5% -10%, rgba(99,102,241,0.10), transparent 60%), radial-gradient(900px 420px at 105% -10%, rgba(168,85,247,0.09), transparent 60%)',
          backgroundAttachment: 'fixed',
          WebkitFontSmoothing: 'antialiased',
        },
        '::selection': { background: alpha(BRAND.primary, 0.18) },
        '@keyframes cubeFadeUp': {
          from: { opacity: 0, transform: 'translateY(8px)' },
          to: { opacity: 1, transform: 'none' },
        },
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
          borderRadius: 12,
          transition: 'transform .15s ease, box-shadow .2s ease, background-color .2s ease',
          '&:focus-visible': { boxShadow: FOCUS_RING },
        },
        sizeLarge: { padding: '11px 22px', fontSize: 15 },
        containedPrimary: {
          backgroundImage: BRAND.gradient,
          boxShadow: '0 8px 20px -10px rgba(79,70,229,0.7)',
          '&:hover': { transform: 'translateY(-1px)', boxShadow: '0 12px 24px -10px rgba(79,70,229,0.75)' },
          '&:active': { transform: 'none' },
          '&.Mui-disabled': { backgroundImage: 'none' },
        },
        containedError: { boxShadow: '0 8px 20px -10px rgba(225,29,72,0.6)' },
        outlined: {
          borderColor: BRAND.line,
          backgroundColor: BRAND.surface,
          color: BRAND.ink,
          '&:hover': { borderColor: alpha(BRAND.primary, 0.4), backgroundColor: alpha(BRAND.primary, 0.03) },
        },
        text: { '&:hover': { backgroundColor: alpha(BRAND.primary, 0.06) } },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          transition: 'background-color .2s ease, color .2s ease',
          '&:hover': { backgroundColor: alpha(BRAND.primary, 0.07) },
          '&:focus-visible': { boxShadow: FOCUS_RING },
        },
      },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        rounded: { borderRadius: 16 },
        outlined: { borderColor: BRAND.line, boxShadow: SOFT_SHADOW },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: {
        root: {
          borderColor: BRAND.line,
          borderRadius: 18,
          boxShadow: SOFT_SHADOW,
          transition: 'box-shadow .25s ease, transform .25s ease, border-color .25s ease',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, borderRadius: 8 },
        outlined: { borderColor: BRAND.line, backgroundColor: BRAND.surface },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: {
          minHeight: 44,
          padding: 4,
          borderRadius: 14,
          backgroundColor: '#EEF0F7',
          display: 'inline-flex',
          maxWidth: '100%',
        },
        indicator: {
          height: '100%',
          borderRadius: 10,
          backgroundColor: BRAND.surface,
          boxShadow: '0 1px 2px rgba(16,24,40,0.08), 0 4px 12px -6px rgba(79,70,229,0.35)',
          zIndex: 0,
        },
        flexContainer: { gap: 2 },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
          minHeight: 36,
          padding: '6px 16px',
          borderRadius: 10,
          zIndex: 1,
          color: BRAND.muted,
          '&.Mui-selected': { color: BRAND.primaryDark },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: BRAND.surface,
          borderRadius: 12,
          transition: 'box-shadow .2s ease',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: BRAND.line },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: alpha(BRAND.primary, 0.35) },
          '&.Mui-focused': { boxShadow: FOCUS_RING },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderWidth: 1, borderColor: BRAND.primary },
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: { borderRadius: 22, boxShadow: '0 40px 80px -24px rgba(15,23,42,0.35)' },
      },
    },
    MuiDialogTitle: { styleOverrides: { root: { fontFamily: HEADING_FONT, fontWeight: 700, fontSize: 20, paddingTop: 22 } } },
    MuiBackdrop: {
      styleOverrides: {
        root: { '&:not(.MuiBackdrop-invisible)': { backgroundColor: 'rgba(15,23,42,0.35)', backdropFilter: 'blur(4px)' } },
      },
    },
    MuiMenu: {
      styleOverrides: {
        paper: { borderRadius: 14, border: `1px solid ${BRAND.line}`, boxShadow: '0 20px 40px -16px rgba(15,23,42,0.25)' },
      },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: { borderRadius: 8, margin: '2px 6px', '&.Mui-selected': { backgroundColor: alpha(BRAND.primary, 0.08) } },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 700,
          color: BRAND.muted,
          fontSize: 11.5,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          backgroundColor: '#F8F9FC',
        },
        root: { borderColor: BRAND.line },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          transition: 'background-color .15s ease',
          '&.MuiTableRow-hover:hover': { backgroundColor: alpha(BRAND.primary, 0.035) },
          '&:last-child td': { borderBottom: 0 },
        },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 14, alignItems: 'center' },
        standardInfo: { backgroundColor: '#EEF2FF', color: '#3730A3', border: '1px solid #E0E7FF' },
        standardSuccess: { border: '1px solid #DCFCE7' },
        standardError: { border: '1px solid #FFE4E6' },
        standardWarning: { border: '1px solid #FEF3C7' },
      },
    },
    MuiTooltip: {
      defaultProps: { arrow: true },
      styleOverrides: {
        tooltip: { borderRadius: 8, fontSize: 12, fontWeight: 500, backgroundColor: BRAND.ink, padding: '6px 10px' },
        arrow: { color: BRAND.ink },
      },
    },
    MuiAvatar: { styleOverrides: { root: { fontWeight: 700 } } },
    MuiPaginationItem: { styleOverrides: { root: { borderRadius: 10, fontWeight: 600 } } },
    MuiLinearProgress: { styleOverrides: { root: { borderRadius: 4 } } },
    MuiSkeleton: { defaultProps: { animation: 'wave' } },
  },
})

export default theme
