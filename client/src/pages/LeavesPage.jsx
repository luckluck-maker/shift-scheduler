import { useEffect, useState } from 'react'
import { api } from '../api/client'
import Modal from '../components/Modal'
import Field from '../components/Field'

const TYPES = {
    VACATION: 'חופשה',
    SICK: 'מחלה',
    TRAINING: 'הכשרה',
    OTHER: 'אחר',
}

// The server stores a row per day but hands back ranges, so a week off is one
// line here and not seven. Cancelling a single day is still possible - it just
// splits the range in two on the next load.
export default function LeavesPage() {
    const [leaves, setLeaves] = useState([])
    const [employees, setEmployees] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [adding, setAdding] = useState(false)

    useEffect(() => {
        load()
    }, [])

    async function load() {
        setLoading(true)

        try {
            const [leaveList, employeeList] = await Promise.all([
                api.get('/api/leaves'),
                api.get('/api/employees'),
            ])

            setLeaves(leaveList)
            setEmployees(employeeList.filter((employee) => employee.active))
            setError(null)
        } catch {
            setError('לא הצלחנו לטעון את ההיעדרויות')
        } finally {
            setLoading(false)
        }
    }

    async function remove(leave) {
        const range = leave.days === 1
            ? formatDate(leave.startDate)
            : `${formatDate(leave.startDate)} – ${formatDate(leave.endDate)}`

        if (!confirm(`למחוק את ההיעדרות של ${leave.employeeName} בתאריכים ${range}?`)) {
            return
        }

        try {
            await api.delete(
                `/api/leaves?employeeId=${leave.employeeId}`
                + `&from=${leave.startDate}&to=${leave.endDate}`)
            await load()
        } catch {
            setError('המחיקה נכשלה')
        }
    }

    if (loading) {
        return <p className="notice">טוען…</p>
    }

    return (
        <>
            <div className="page-head">
                <h1>היעדרויות</h1>
                <button onClick={() => setAdding(true)}>הוספת היעדרות</button>
            </div>

            {error && <p className="error">{error}</p>}

            {leaves.length === 0 ? (
                <p className="notice">אין היעדרויות רשומות.</p>
            ) : (
                <table className="table">
                    <thead>
                    <tr>
                        <th>עובד</th>
                        <th>תאריכים</th>
                        <th>סוג</th>
                        <th />
                    </tr>
                    </thead>
                    <tbody>
                    {leaves.map((leave) => (
                        <tr key={`${leave.employeeId}-${leave.startDate}`}>
                            <td>{leave.employeeName}</td>
                            <td className="cell-muted">
                  <span className="time-range">
                    {formatDate(leave.startDate)}
                      {leave.days > 1 && ` – ${formatDate(leave.endDate)}`}
                  </span>
                                <span className="tag-soft">
                    {leave.days === 1 ? 'יום אחד' : `${leave.days} ימים`}
                  </span>
                            </td>
                            <td>{TYPES[leave.type] ?? leave.type}</td>
                            <td className="row-actions">
                                <button className="link-button danger" onClick={() => remove(leave)}>
                                    מחיקה
                                </button>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}

            {adding && (
                <LeaveForm
                    employees={employees}
                    onClose={() => setAdding(false)}
                    onSaved={() => {
                        setAdding(false)
                        load()
                    }}
                />
            )}
        </>
    )
}

function LeaveForm({ employees, onClose, onSaved }) {
    const [form, setForm] = useState({
        employeeId: '',
        startDate: '',
        endDate: '',
        type: 'VACATION',
    })

    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    function set(field, value) {
        setForm((current) => ({ ...current, [field]: value }))
    }

    // Picking a start date fills in the end date too, since most leave is a
    // single day and retyping the same date is annoying.
    function setStart(value) {
        setForm((current) => ({
            ...current,
            startDate: value,
            endDate: current.endDate || value,
        }))
    }

    async function submit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)

        try {
            await api.post('/api/leaves', {
                employeeId: Number(form.employeeId),
                startDate: form.startDate,
                endDate: form.endDate,
                type: form.type,
            })

            onSaved()
        } catch (err) {
            setError(messageFor(err))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Modal title="היעדרות חדשה" onClose={onClose}>
            <form onSubmit={submit} className="form">
                <Field label="עובד">
                    <select value={form.employeeId}
                            onChange={(e) => set('employeeId', e.target.value)} required autoFocus>
                        <option value="">בחר עובד</option>
                        {employees.map((employee) => (
                            <option key={employee.id} value={employee.id}>{employee.fullName}</option>
                        ))}
                    </select>
                </Field>

                <div className="field-row">
                    <Field label="מתאריך">
                        <input type="date" value={form.startDate}
                               onChange={(e) => setStart(e.target.value)} required />
                    </Field>

                    <Field label="עד תאריך">
                        <input type="date" value={form.endDate} min={form.startDate}
                               onChange={(e) => set('endDate', e.target.value)} required />
                    </Field>
                </div>

                <Field label="סוג">
                    <select value={form.type} onChange={(e) => set('type', e.target.value)}>
                        {Object.entries(TYPES).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                        ))}
                    </select>
                </Field>

                {error && <p className="error">{error}</p>}

                <div className="form-actions">
                    <button type="button" className="link-button" onClick={onClose}>ביטול</button>
                    <button type="submit" disabled={saving}>{saving ? 'שומר…' : 'שמירה'}</button>
                </div>
            </form>
        </Modal>
    )
}

function formatDate(iso) {
    const [year, month, day] = iso.split('-')
    return `${day}/${month}/${year}`
}

function messageFor(error) {
    if (error.status === 409) {
        return 'לעובד כבר רשומה היעדרות באחד מהתאריכים האלה'
    }

    if (error.status === 400) {
        return 'תאריך הסיום לא יכול להיות לפני תאריך ההתחלה'
    }

    return 'השמירה נכשלה'
}
