import { useEffect, useState } from 'react'
import { api } from '../api/client'
import Modal from '../components/Modal'
import Field from '../components/Field'

// The two lists everything else is built on: an employee holds a position, a
// shift is of a type. Both are short, so they sit on one screen.
export default function PositionsAndShiftTypesPage() {
    const [positions, setPositions] = useState([])
    const [shiftTypes, setShiftTypes] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    const [editingPosition, setEditingPosition] = useState(null)
    const [editingShiftType, setEditingShiftType] = useState(null)

    useEffect(() => {
        load()
    }, [])

    async function load() {
        setLoading(true)

        try {
            const [positionList, shiftTypeList] = await Promise.all([
                api.get('/api/job-positions'),
                api.get('/api/shift-types'),
            ])

            setPositions(positionList)
            setShiftTypes(shiftTypeList)
            setError(null)
        } catch {
            setError('לא הצלחנו לטעון את הנתונים')
        } finally {
            setLoading(false)
        }
    }

    async function removePosition(position) {
        if (!confirm(`למחוק את התפקיד ${position.name}?`)) {
            return
        }

        try {
            await api.delete(`/api/job-positions/${position.id}`)
            await load()
        } catch (err) {
            setError(err.status === 409
                ? 'לא ניתן למחוק תפקיד שמשויכים אליו עובדים'
                : 'המחיקה נכשלה')
        }
    }

    async function removeShiftType(shiftType) {
        if (!confirm(`למחוק את סוג המשמרת ${shiftType.name}?\n`
            + 'מחיקת סוג המשמרת תמחק את כל המשמרות בסידורים שטרם פורסמו.\n'
            + 'סידורים מפורסמים לא ישתנו.')) {
            return
        }

        try {
            await api.delete(`/api/shift-types/${shiftType.id}`)
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
                <h1>תפקידים ומשמרות</h1>
            </div>

            {error && <p className="error">{error}</p>}

            {/* The two lists sit side by side rather than one under the other. */}
            <div className="two-columns">
                <section className="section">
                    <div className="section-head">
                        <h2>תפקידים</h2>
                        <button className="secondary" onClick={() => setEditingPosition({ name: '' })}>הוספת תפקיד</button>
                    </div>

                    <table className="table">
                        <tbody>
                        {positions.map((position) => (
                            <tr key={position.id}>
                                <td>{position.name}</td>
                                <td className="row-actions">
                                    <button className="secondary"
                                            onClick={() => setEditingPosition(position)}>עריכה</button>
                                    <button className="danger"
                                            onClick={() => removePosition(position)}>מחיקה</button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </section>

                <section className="section">
                    <div className="section-head">
                        <h2>סוגי משמרת</h2>
                        <button className="secondary" onClick={() => setEditingShiftType({ name: '', startTime: '', endTime: '' })}>
                            הוספת סוג משמרת
                        </button>
                    </div>

                    <table className="table">
                        <tbody>
                        {shiftTypes.map((shiftType) => (
                            <tr key={shiftType.id}>
                                <td>{shiftType.name}</td>
                                <td className="cell-muted">
                                  <span className="time-range">
                                    {shiftType.startTime}–{shiftType.endTime}
                                  </span>
                                    <span className="tag-soft">{shiftType.durationHours} שעות</span>
                                    {shiftType.crossesMidnight && <span className="tag-soft">חוצה חצות</span>}
                                </td>
                                <td className="row-actions">
                                    <button className="secondary"
                                            onClick={() => setEditingShiftType(shiftType)}>עריכה</button>
                                    <button className="danger"
                                            onClick={() => removeShiftType(shiftType)}>מחיקה</button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </section>
            </div>

            <p className="hint">
                מחיקת סוג משמרת מוחקת את כל המשמרות שלו בסידורים שטרם פורסמו,
                יחד עם השיבוצים שבהן. סידורים שכבר פורסמו לא ישתנו.
            </p>

            {editingPosition && (
                <PositionForm
                    position={editingPosition}
                    onClose={() => setEditingPosition(null)}
                    onSaved={() => {
                        setEditingPosition(null)
                        load()
                    }}
                />
            )}

            {editingShiftType && (
                <ShiftTypeForm
                    shiftType={editingShiftType}
                    onClose={() => setEditingShiftType(null)}
                    onSaved={() => {
                        setEditingShiftType(null)
                        load()
                    }}
                />
            )}
        </>
    )
}

function PositionForm({ position, onClose, onSaved }) {
    const [name, setName] = useState(position.name)
    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    const isNew = !position.id

    async function submit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)

        try {
            if (isNew) {
                await api.post('/api/job-positions', { name })
            } else {
                await api.put(`/api/job-positions/${position.id}`, { name })
            }

            onSaved()
        } catch (err) {
            setError(err.status === 409 ? 'השם כבר קיים' : 'השמירה נכשלה')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Modal title={isNew ? 'תפקיד חדש' : 'עריכת תפקיד'} onClose={onClose}>
            <form onSubmit={submit} className="form">
                <Field label="שם התפקיד">
                    <input value={name} onChange={(e) => setName(e.target.value)}
                           maxLength={40} required autoFocus />
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

// crossesMidnight isn't asked for - the server works it out from the times,
// so a night shift can't end up stored as if it ran backwards.
function ShiftTypeForm({ shiftType, onClose, onSaved }) {
    const [form, setForm] = useState(shiftType)
    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    const isNew = !shiftType.id

    function set(field, value) {
        setForm((current) => ({ ...current, [field]: value }))
    }

    async function submit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)

        const body = {
            name: form.name,
            startTime: form.startTime,
            endTime: form.endTime,
        }

        try {
            if (isNew) {
                await api.post('/api/shift-types', body)
            } else {
                await api.put(`/api/shift-types/${shiftType.id}`, body)
            }

            onSaved()
        } catch (err) {
            setError(messageFor(err))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Modal title={isNew ? 'סוג משמרת חדש' : 'עריכת סוג משמרת'} onClose={onClose}>
            <form onSubmit={submit} className="form">
                <Field label="שם">
                    <input value={form.name} onChange={(e) => set('name', e.target.value)}
                           maxLength={40} required autoFocus />
                </Field>

                <div className="field-row">
                    <Field label="שעת התחלה">
                        <input type="time" value={form.startTime}
                               onChange={(e) => set('startTime', e.target.value)} required />
                    </Field>

                    <Field label="שעת סיום">
                        <input type="time" value={form.endTime}
                               onChange={(e) => set('endTime', e.target.value)} required />
                    </Field>
                </div>

                <p className="hint">
                    משמרת שנגמרת לפני שהיא מתחילה תסומן אוטומטית כחוצה חצות.
                </p>

                {error && <p className="error">{error}</p>}

                <div className="form-actions">
                    <button type="button" className="link-button" onClick={onClose}>ביטול</button>
                    <button type="submit" disabled={saving}>{saving ? 'שומר…' : 'שמירה'}</button>
                </div>
            </form>
        </Modal>
    )
}

function messageFor(error) {
    if (error.status === 409) {
        return 'השם כבר קיים'
    }

    if (error.status === 400) {
        return 'שעת ההתחלה והסיום חייבות להיות שונות'
    }

    return 'השמירה נכשלה'
}
