export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '/cube').replace(/\/$/, '')

export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const MAX_FILES_PER_UPLOAD = 20
export const MAX_KEYWORDS = 20
export const MAX_KEYWORD_LENGTH = 50
export const MAX_TITLE_LENGTH = 255
export const PAGE_SIZE = 10

/** Permission codes as stored in CUBEDB (MODULE_CODE:ACCESS_CODE). */
export const PERM = {
  VIEW: 'KNOWLEDGE:VIEW',
  SEARCH: 'KNOWLEDGE:SEARCH',
  DOWNLOAD: 'KNOWLEDGE:DOWNLOAD',
  SUBMIT: 'KNOWLEDGE:CREATE',
  APPROVE: 'KNOWLEDGE:APPROVE',
  EDIT: 'KNOWLEDGE:UPDATE',
  DELETE: 'KNOWLEDGE:DELETE',
  MANAGE_USERS: 'AUTH:MANAGE_USERS',
  MANAGE_GROUPS: 'AUTH:MANAGE_GROUPS',
  MANAGE_MODULES: 'AUTH:MANAGE_MODULES',
  MANAGE_ACCESS: 'AUTH:MANAGE_ACCESS',
  MANAGE_GROUP_ACCESS: 'AUTH:MANAGE_GROUP_ACCESS',
  MANAGE_USER_ACCESS: 'AUTH:MANAGE_USER_ACCESS',
}

/** Any of these shows the Admin menu. */
export const ADMIN_PERMS = [
  PERM.MANAGE_USERS,
  PERM.MANAGE_GROUPS,
  PERM.MANAGE_MODULES,
  PERM.MANAGE_ACCESS,
  PERM.MANAGE_GROUP_ACCESS,
  PERM.MANAGE_USER_ACCESS,
]

/** Friendly names for known permissions; anything else falls back to its description or code. */
export const PERMISSION_LABELS = {
  [PERM.VIEW]: 'View',
  [PERM.SEARCH]: 'Search',
  [PERM.DOWNLOAD]: 'Download',
  [PERM.SUBMIT]: 'Submit',
  [PERM.APPROVE]: 'Approve',
  [PERM.EDIT]: 'Edit any',
  [PERM.DELETE]: 'Delete any',
  [PERM.MANAGE_USERS]: 'Manage users',
  [PERM.MANAGE_GROUPS]: 'Manage groups',
  [PERM.MANAGE_MODULES]: 'Manage modules',
  [PERM.MANAGE_ACCESS]: 'Manage access codes',
  [PERM.MANAGE_GROUP_ACCESS]: 'Group permissions',
  [PERM.MANAGE_USER_ACCESS]: 'User overrides',
}

export const MODULE_LABELS = { KNOWLEDGE: 'Knowledge', AUTH: 'Administration' }

export function permissionLabel(code, description) {
  return PERMISSION_LABELS[code] || description || code
}

export const STATUSES = ['DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED']

/** Job titles offered as suggestions for a user's role. Any other title can be typed. Not a permission. */
export const DESIGNATIONS = [
  'Senior Vice President',
  'Vice President',
  'Senior Manager',
  'Manager',
  'Team Lead',
  'Senior Employee',
  'Employee',
  'Intern',
  'Consultant',
]

/** Reporting ladder: same rules as the backend (Levels.java). 3 = Team Lead, 4 = Manager. */
export const LEVEL = { TEAM_LEAD: 3, MANAGER: 4 }
export const LEVEL_NAMES = ['Admin', 'Employee', 'Senior Employee', 'Team Lead', 'Manager', 'Senior Manager', 'Vice President', 'Senior Vice President']

export function designationLevel(designation, groupCode) {
  const d = (designation || '').trim().toLowerCase()
  if (d) {
    if (d.includes('senior vice president') || /\bsvp\b/.test(d)) return 7
    if (d.includes('vice president') || /\bvp\b/.test(d)) return 6
    if (d.includes('senior manager') || d.includes('director')) return 5
    if (d.includes('manager') || d.includes('head')) return 4
    if (d.includes('lead')) return 3
    if (d.includes('senior')) return 2
    if (groupCode !== 'MANAGER') return 1
  }
  if (groupCode === 'MANAGER') return LEVEL.MANAGER
  return groupCode === 'ADMIN' ? 0 : 1
}

/** Can someone at personLevel report to option (needs Team Lead or higher, and not below them)? */
export const canReportTo = (option, personLevel) =>
  (option.level ?? 0) >= LEVEL.TEAM_LEAD && (option.level ?? 0) >= personLevel
