import { useEffect, useState } from 'react'
import { api } from '../api/client'
import Modal from '../components/Modal'
import Field from '../components/Field'

// Copied on every add, canceled form doesn't leave anything behind.
const EMPTY = {
    fullName: '',
    username: '',
    password: '',
    role: 'EMPLOYEE',
    maxWeeklyHours: 40,
    jobPositionId: '',
}

export default function EmployeesPage() {
    const [employees, setEmployees] = useState([])
    const [positions, setPositions] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    // null when the form is closed, the employee being edited otherwise.
    // A new employee is an object with no id.
    const [editing, setEditing] = useState(null)

    useEffect(() => {
        load()
    }, [])

    async function load() {
        setLoading(true)

        try {
            const [employeeList, positionList] = await Promise.all([
                api.get('/api/employees'),
                api.get('/api/job-positions'),
            ])

            setEmployees(employeeList)
            setPositions(positionList)
            setError(null)
        } catch {
            setError('לא הצלחנו לטעון את רשימת העובדים')
        } finally {
            setLoading(false)
        }
    }

    async function deactivate(employee) {
        if (!confirm(`להשבית את ${employee.fullName}?`)) {
            return
        }

        try {
            await api.delete(`/api/employees/${employee.id}?version=${employee.version}`)
            await load()
        } catch (err) {
            if (err.code === 'STALE_VERSION') {
                setError('הפרטים שונו במקביל. רענן ונסה שוב')
            } else if (err.code === 'WRONG_STATUS') {
                setError('מתבצעת כעת בניית סידור. יש להמתין לסיומה ולנסות שוב')
            } else if (err.code === 'LAST_MANAGER') {
                setError('חייב להישאר לפחות מנהל אחד פעיל')
            } else {
                setError('ההשבתה נכשלה')
            }
        }
    }

    // Brings a disabled employee back.
    async function activate(employee) {
        if (!confirm(`להחזיר את ${employee.fullName} לפעילות?`)) {
            return
        }

        try {
            await api.put(`/api/employees/${employee.id}/activate?version=${employee.version}`)
            await load()
        } catch (err) {
            if (err.code === 'STALE_VERSION') {
                setError('הפרטים שונו במקביל. רענן ונסה שוב')
            } else if (err.code === 'WRONG_STATUS') {
                setError('מתבצעת כעת בניית סידור. יש להמתין לסיומה ולנסות שוב')
            } else if (err.status === 400) {
                setError('התפקיד של העובד אינו בשימוש. יש לעדכן לו תפקיד תחילה')
            } else {
                setError('ההפעלה נכשלה')
            }
        }
    }

    if (loading) {
        return <p className="notice">טוען…</p>
    }

    const activeCount = employees.filter((employee) => employee.active).length

    return (
        <>
            <div className="page-head">
                <div>
                    <h1>עובדים</h1>
                    <p className="page-sub">
                        {activeCount} פעיל{activeCount === 1 ? '' : 'ים'}
                        {' · '}
                        {employees.length - activeCount} מושבת{employees.length - activeCount === 1 ? '' : 'ים'}
                    </p>
                </div>

                <button onClick={() => setEditing({ ...EMPTY })}>הוספת עובד</button>
            </div>

            {error && <p className="error">{error}</p>}

            <div className="table-scroll">
                <table className="table">
                    <thead>
                    <tr>
                        <th>שם</th>
                        <th>דוא״ל</th>
                        <th>תפקיד</th>
                        <th>שעות שבועיות</th>
                        <th>סטטוס</th>
                        <th />
                    </tr>
                    </thead>
                    <tbody>
                    {employees.map((employee) => (
                        <tr key={employee.id} className={employee.active ? '' : 'row-muted'}>
                            <td>
                                <span className="cell-clip" title={employee.fullName}>
                                    {employee.fullName}
                                </span>
                            </td>
                            <td className="ltr">
                                <span className="cell-clip" title={employee.username}>
                                    {employee.username}
                                </span>
                            </td>
                            <td>
                                <span className="cell-clip" title={employee.jobPositionName}>
                                    {employee.jobPositionName}
                                </span>
                            </td>
                            <td className="ltr">{employee.maxWeeklyHours}</td>
                            <td>
                                {employee.active ? 'פעיל' : 'מושבת'}
                                {employee.role === 'MANAGER' && <span className="tag">מנהל</span>}
                            </td>
                            <td className="row-actions">
                                <button className="secondary" onClick={() => setEditing(employee)}>
                                    עריכה
                                </button>
                                {employee.active ? (
                                    <button className="danger" onClick={() => deactivate(employee)}>
                                        השבתה
                                    </button>
                                ) : (
                                    <button className="secondary" onClick={() => activate(employee)}>
                                        הפעלה
                                    </button>
                                )}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {editing && (
                <EmployeeForm
                    employee={editing}
                    positions={positions}
                    onClose={() => setEditing(null)}
                    onSaved={() => {
                        setEditing(null)
                        load()
                    }}
                />
            )}
        </>
    )
}

// One form for both creating and editing. They send different fields - a new
// employee needs a password, an existing one carries a version - so the two are
// kept apart when submitting.
function EmployeeForm({ employee, positions, onClose, onSaved }) {
    const [form, setForm] = useState(employee)
    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    const isNew = !employee.id

    function set(field, value) {
        setForm((current) => ({ ...current, [field]: value }))
    }

    async function submit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)

        // The details and the password are two calls. If the second one fails the
        // first is already saved, and the retry comes back as a stale version.
        try {
            if (isNew) {
                await api.post('/api/employees', {
                    fullName: form.fullName,
                    username: form.username,
                    password: form.password,
                    role: form.role,
                    maxWeeklyHours: Number(form.maxWeeklyHours),
                    jobPositionId: Number(form.jobPositionId),
                })
            } else {
                await api.put(`/api/employees/${employee.id}`, {
                    version: employee.version,
                    fullName: form.fullName,
                    role: form.role,
                    maxWeeklyHours: Number(form.maxWeeklyHours),
                    jobPositionId: Number(form.jobPositionId),
                })
                if (form.password) {
                    await api.put(`/api/employees/${employee.id}/password`,
                        { newPassword: form.password })
                }
            }

            onSaved()
        } catch (err) {
            setError(messageFor(err, isNew))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Modal title={isNew ? 'עובד חדש' : 'עריכת עובד'} onClose={onClose}>
            <form onSubmit={submit} className="form">
                <Field label="שם מלא">
                    <input value={form.fullName}
                           onChange={(e) => set('fullName', e.target.value)}
                           maxLength={100} required />
                </Field>

                {/* Only when adding. The address is the login name, and the update
                    request has no field for it. */}
                {isNew && (
                    <Field label="דוא״ל">
                        <input type="email" value={form.username}
                               onChange={(e) => set('username', e.target.value)}
                               maxLength={254} required />
                    </Field>
                )}

                {/* Required for a new employee, optional when editing - left empty
                    the password simply stays as it is. */}
                <Field label={isNew ? 'סיסמה' : 'סיסמה חדשה'}>
                    <input type="password" value={form.password ?? ''}
                           onChange={(e) => set('password', e.target.value)}
                           minLength={8} maxLength={64} required={isNew}
                           placeholder={isNew ? undefined : 'השאר ריק כדי לא לשנות'} />
                </Field>

                <Field label="תפקיד">
                    <select value={form.jobPositionId}
                            onChange={(e) => set('jobPositionId', e.target.value)}
                            required>
                        <option value="">בחר תפקיד</option>
                        {positions.map((position) => (
                            <option key={position.id} value={position.id}>{position.name}</option>
                        ))}
                    </select>
                </Field>

                <Field label="שעות שבועיות">
                    <input type="number" min={1} max={168} value={form.maxWeeklyHours}
                           onChange={(e) => set('maxWeeklyHours', e.target.value)}
                           required />
                </Field>

                <Field label="הרשאה">
                    <select value={form.role} onChange={(e) => set('role', e.target.value)}>
                        <option value="EMPLOYEE">עובד</option>
                        <option value="MANAGER">מנהל</option>
                    </select>
                </Field>

                {error && <p className="error">{error}</p>}

                <div className="form-actions">
                    <button type="button" className="link-button" onClick={onClose}>ביטול</button>
                    <button type="submit" disabled={saving}>
                        {saving ? 'שומר…' : 'שמירה'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

// The two codes come first, because 400 and 409 are each used for more than
// one thing.
function messageFor(error, isNew) {
    if (error.code === 'LAST_MANAGER') {
        return 'חייב להישאר לפחות מנהל אחד פעיל'
    }

    if (error.code === 'STALE_VERSION') {
        return 'הפרטים שונו במקביל. רענן ונסה שוב'
    }

    if (error.status === 409) {
        return 'כתובת הדוא״ל כבר רשומה במערכת'
    }

    if (error.status === 400) {
        return 'חלק מהפרטים אינם תקינים'
    }

    return 'השמירה נכשלה'
}
