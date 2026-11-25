import { request } from './client'
import { PAGE_SIZE } from '../config'

const V1 = '/api/v1'

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value)
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

function knowledgeForm({ title, description, keywords = [], files = [] }) {
  const form = new FormData()
  form.append('title', title ?? '')
  form.append('description', description ?? '')
  keywords.forEach((keyword) => form.append('keywords', keyword))
  files.forEach((file) => form.append('files', file))
  return form
}

export const authApi = {
  login: (username, password) =>
    request(`${V1}/auth/login`, { method: 'POST', body: { username, password }, auth: false }),
  logout: () => request(`${V1}/auth/logout`, { method: 'POST' }),
  me: () => request(`${V1}/me`),
  permissions: () => request(`${V1}/me/permissions`),
  changePassword: (currentPassword, newPassword) =>
    request(`${V1}/me/password`, { method: 'POST', body: { currentPassword, newPassword } }),
}

export const knowledgeApi = {
  published: (page = 0, size = PAGE_SIZE) => request(`${V1}/knowledge/published${query({ page, size })}`),
  search: (q, page = 0, size = PAGE_SIZE) => request(`${V1}/knowledge/search${query({ q, page, size })}`),
  mine: (status, page = 0, size = PAGE_SIZE) => request(`${V1}/knowledge/mine${query({ status, page, size })}`),
  byStatus: (status, page = 0, size = PAGE_SIZE) => request(`${V1}/knowledge${query({ status, page, size })}`),
  get: (id) => request(`${V1}/knowledge/${id}`),

  /** Creates an item with keywords and files in one multipart request. */
  create: ({ draft = false, ...fields }) =>
    request(`${V1}/knowledge/${draft ? 'draft' : 'submit'}`, { method: 'POST', body: knowledgeForm(fields) }),
  update: (id, { title, description, keywords }) =>
    request(`${V1}/knowledge/${id}`, { method: 'PUT', body: { title, description, keywords } }),
  submit: (id) => request(`${V1}/knowledge/${id}/submit`, { method: 'POST' }),
  approve: (id) => request(`${V1}/knowledge/${id}/approve`, { method: 'POST' }),
  reject: (id, reason) => request(`${V1}/knowledge/${id}/reject`, { method: 'POST', body: { reason } }),
  archive: (id) => request(`${V1}/knowledge/${id}/archive`, { method: 'POST' }),
  restore: (id) => request(`${V1}/knowledge/${id}/restore`, { method: 'POST' }),
  remove: (id) => request(`${V1}/knowledge/${id}`, { method: 'DELETE' }),

  bookmarks: (page = 0, size = PAGE_SIZE) => request(`${V1}/knowledge/bookmarks${query({ page, size })}`),
  bookmark: (id) => request(`${V1}/knowledge/${id}/bookmark`, { method: 'PUT' }),
  removeBookmark: (id) => request(`${V1}/knowledge/${id}/bookmark`, { method: 'DELETE' }),

  attachments: (id) => request(`${V1}/knowledge/${id}/attachments`),
  addAttachments: (id, files) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    return request(`${V1}/knowledge/${id}/attachments`, { method: 'POST', body: form })
  },
}

export const attachmentApi = {
  /** mode: "download" (attachment disposition) or "preview" (inline for safe types). */
  async blob(attachmentId, mode = 'download') {
    const response = await request(`${V1}/attachments/${attachmentId}/${mode}`, {
      raw: true,
      headers: { Accept: '*/*' },
    })
    return response.blob()
  },
  remove: (attachmentId) => request(`${V1}/attachments/${attachmentId}`, { method: 'DELETE' }),
}

/** Managers: the employees who report to them. */
export const teamApi = {
  list: () => request(`${V1}/team`),
  managers: () => request(`${V1}/team/managers`),
  create: (body) => request(`${V1}/team`, { method: 'POST', body }),
  update: (id, body) => request(`${V1}/team/${id}`, { method: 'PUT', body }),
  resetPassword: (id, password) => request(`${V1}/team/${id}/password`, { method: 'PUT', body: { password } }),
  /** Someone who left: login blocked, hidden, their team moves up to their manager. Knowledge stays. */
  remove: (id) => request(`${V1}/team/${id}`, { method: 'DELETE' }),
}

export const adminApi = {
  approvers: () => request(`${V1}/admin/users/approvers`),
  /** Every active user: who someone can report to (managers, team leads, ...). */
  reportingOptions: () => request(`${V1}/admin/users/options`),
  /** CSV with username,email,firstName,lastName,employeeId,designation,group,reportingManager */
  importUsers: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request(`${V1}/admin/users/import`, { method: 'POST', body: form })
  },
  storageStatus: () => request(`${V1}/admin/storage/status`),
  migrateStorage: () => request(`${V1}/admin/storage/migrate`, { method: 'POST' }),
  users: (q, page = 0, size = 20, removed = false) =>
    request(`${V1}/admin/users${query({ q, page, size, removed: removed ? 'true' : undefined })}`),
  removeUser: (id) => request(`${V1}/admin/users/${id}`, { method: 'DELETE' }),
  restoreUser: (id) => request(`${V1}/admin/users/${id}/restore`, { method: 'POST' }),
  createUser: (body) => request(`${V1}/admin/users`, { method: 'POST', body }),
  updateUser: (id, body) => request(`${V1}/admin/users/${id}`, { method: 'PUT', body }),
  resetPassword: (id, password) => request(`${V1}/admin/users/${id}/password`, { method: 'PUT', body: { password } }),
  userPermissions: (id) => request(`${V1}/admin/users/${id}/permissions`),
  overrides: (id) => request(`${V1}/admin/users/${id}/overrides`),
  upsertOverride: (id, body) => request(`${V1}/admin/users/${id}/overrides`, { method: 'PUT', body }),
  deleteOverride: (id, moduleCode, accessCode) =>
    request(`${V1}/admin/users/${id}/overrides/${moduleCode}/${accessCode}`, { method: 'DELETE' }),

  groups: () => request(`${V1}/admin/groups`),
  createGroup: (body) => request(`${V1}/admin/groups`, { method: 'POST', body }),
  updateGroup: (id, body) => request(`${V1}/admin/groups/${id}`, { method: 'PUT', body }),
  deleteGroup: (id) => request(`${V1}/admin/groups/${id}`, { method: 'DELETE' }),
  grants: (id) => request(`${V1}/admin/groups/${id}/grants`),
  upsertGrant: (id, body) => request(`${V1}/admin/groups/${id}/grants`, { method: 'PUT', body }),
  deleteGrant: (id, moduleCode, accessCode) =>
    request(`${V1}/admin/groups/${id}/grants/${moduleCode}/${accessCode}`, { method: 'DELETE' }),

  audit: (actor, page = 0, size = 25) => request(`${V1}/admin/audit${query({ actor, page, size })}`),

  /** [{ id, code, moduleCode, permission, name, description, status }] */
  accessCodes: () => request(`${V1}/admin/access-codes`),
  modules: () => request(`${V1}/admin/modules`),
}
