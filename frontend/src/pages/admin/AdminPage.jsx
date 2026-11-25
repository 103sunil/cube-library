import AdminIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import { useSearchParams } from 'react-router-dom'
import { Box, Tab, Tabs } from '@mui/material'
import { PageHeader } from '../../components/common'
import { useAuth } from '../../auth/AuthContext'
import { PERM } from '../../config'
import UsersTab from './UsersTab'
import GroupsTab from './GroupsTab'
import AuditTab from './AuditTab'
import StorageTab from './StorageTab'

const TABS = [
  { value: 'users', label: 'Users', Component: UsersTab, anyOf: [PERM.MANAGE_USERS] },
  {
    value: 'groups',
    label: 'Groups & permissions',
    Component: GroupsTab,
    anyOf: [PERM.MANAGE_GROUPS, PERM.MANAGE_GROUP_ACCESS],
  },
  { value: 'storage', label: 'File storage', Component: StorageTab, anyOf: [PERM.MANAGE_MODULES] },
  { value: 'audit', label: 'Audit log', Component: AuditTab, anyOf: [PERM.MANAGE_USERS] },
]

export default function AdminPage() {
  const auth = useAuth()
  const [params, setParams] = useSearchParams()
  const tabs = TABS.filter((t) => t.anyOf.some((p) => auth.can(p)))
  const current = tabs.find((t) => t.value === params.get('tab')) || tabs[0]

  return (
    <Box>
      <PageHeader title="Administration" icon={AdminIcon} subtitle="Manage who can use CUBE and what they can do" />
      {current ? (
        <>
          <Tabs
            value={current.value}
            onChange={(_, value) => setParams({ tab: value })}
            variant="scrollable"
            sx={{ mb: 3 }}
          >
            {tabs.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} />
            ))}
          </Tabs>
          <current.Component />
        </>
      ) : (
        <Box sx={{ color: 'text.secondary' }}>Your administration rights don&apos;t include any of these sections.</Box>
      )}
    </Box>
  )
}
