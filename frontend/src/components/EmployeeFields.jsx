import { Autocomplete, TextField } from '@mui/material'
import { DESIGNATIONS } from '../config'

/** Employee ID + Role (job title) inputs shared by the team and admin dialogs. */
export function EmployeeFields({ form, setForm, fieldErrors = {} }) {
  return (
    <>
      <TextField
        label="Employee ID"
        value={form.employeeCode}
        onChange={(e) => setForm((f) => ({ ...f, employeeCode: e.target.value }))}
        error={Boolean(fieldErrors.employeeCode)}
        helperText={fieldErrors.employeeCode || 'Company employee number'}
        slotProps={{ htmlInput: { maxLength: 30 } }}
      />
      <Autocomplete
        freeSolo
        options={DESIGNATIONS}
        value={form.designation}
        onInputChange={(_, value) => setForm((f) => ({ ...f, designation: value }))}
        renderInput={(params) => (
          <TextField
            {...params}
            label="Designation (job title)"
            error={Boolean(fieldErrors.designation)}
            helperText={fieldErrors.designation || 'Sets their level: decides who they can report to'}
            slotProps={{
              ...params.slotProps,
              htmlInput: { ...(params.slotProps?.htmlInput || params.inputProps), maxLength: 100 },
            }}
          />
        )}
      />
    </>
  )
}
