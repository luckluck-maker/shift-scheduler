import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import ShiftGrid from '../components/ShiftGrid'
import WeekPicker from '../components/WeekPicker'

const TYPES = [
    { value: null, label: 'יכול' },
    { value: 'PREFERS_NOT', label: 'מעדיף שלא' },
    { value: 'CANNOT', label: 'לא יכול' },
]

const LABELS = {
    PREFERS_NOT: 'מעדיף שלא',
    CANNOT: 'לא יכול',
}

// The picker's value when the manager is looking at everyone rather than one
// person's week.
const ALL = 'all'

// An employee sees their own week; a manager picks whose week to look at, which
// is how constraints get corrected once the submission window has closed.
export default function ConstraintsPage() {
    const { user, isManager } = useAuth()

    const [weeks, setWeeks] = useState([])
    const [week, setWeek] = useState(null)
    const [employees, setEmployees] = useState([])

    const [employeeId, setEmployeeId] = useState(isManager ? ALL : user.employeeId)
    const [allPreferences, setAllPreferences] = useState([])

    const [myWeek, setMyWeek] = useState(null)
    const [selected, setSelected] = useState(new Set())
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [busy, setBusy] = useState(false)

    useEffect(() => {
        const calls = [api.get('/api/schedules')]

        if (isManager) {
            calls.push(api.get('/api/employees'))
        }

        Promise.all(calls)
            .then(([weekList, employeeList]) => {
                setWeeks(weekList)
                setWeek(weekList[0] ?? null)

                if (employeeList) {
                    setEmployees(employeeList.filter((employee) => employee.active))
                }
            })
            .catch(() => setError('לא הצלחנו לטעון את הנתונים'))
            .finally(() => setLoading(false))
    }, [isManager])

    useEffect(() => {
        if (week) {
            loadWeek()
        }
    }, [week, employeeId])

    // Clicking away or pressing Escape drops the selection. However, the grid and the
    // panel are excluded - pressing inside won't cancel the selection
    useEffect(() => {
        if (selected.size === 0) {
            return
        }

        function onClickAway(event) {
            if (!event.target.closest('.grid') && !event.target.closest('.panel')) {
                setSelected(new Set())
            }
        }

        function onKey(event) {
            if (event.key === 'Escape') {
                setSelected(new Set())
            }
        }

        window.addEventListener('mousedown', onClickAway)
        window.addEventListener('keydown', onKey)

        return () => {
            window.removeEventListener('mousedown', onClickAway)
            window.removeEventListener('keydown', onKey)
        }
    }, [selected.size])

    async function loadWeek(keepSelection = false) {
        if (!keepSelection) {
            setSelected(new Set())
        }

        try {
            // The overview needs the whole week's constraints rather than one
            // person's, so it asks for them separately.
            const query = isManager && employeeId !== ALL ? `?employeeId=${employeeId}` : ''

            const calls = [api.get(`/api/schedules/${week.id}/my-week${query}`)]

            if (employeeId === ALL) {
                calls.push(api.get(`/api/shift-preferences?scheduleId=${week.id}`))
            }

            const [weekData, preferences] = await Promise.all(calls)

            setMyWeek(weekData)
            setAllPreferences(preferences ?? [])
            setError(null)
        } catch {
            setError('לא הצלחנו לטעון את המשמרות')
        }
    }

    const selectedShifts = myWeek
        ? myWeek.shifts.filter((shift) => selected.has(shift.shiftId))
        : []

    // The type applies to every selected shift, so one that's already right is
    // skipped rather than deleted and rewritten.
    async function applyType(type) {
        setBusy(true)

        try {
            for (const shift of selectedShifts) {
                const current = shift.preferenceType ?? null

                if (current === type) {
                    continue
                }

                if (type === null) {
                    await api.delete(`/api/shift-preferences/${shift.preferenceId}`)
                } else if (current === null) {
                    await api.post('/api/shift-preferences', {
                        shiftId: shift.shiftId,
                        type,
                        employeeId: isManager ? employeeId : undefined,
                    })
                } else {
                    await api.put(`/api/shift-preferences/${shift.preferenceId}`, {
                        type,
                        reason: shift.preferenceReason,
                    })
                }
            }

        } catch (err) {
            setError(err.status === 409
                ? 'תקופת הגשת האילוצים לשבוע זה נסגרה'
                : 'השמירה נכשלה')
            await loadWeek(true)
        } finally {
            setBusy(false)
        }
    }

    // Saved when the field loses focus, so there's no button to press.
    async function applyReason(reason, shifts) {
        const withPreference = shifts.filter((shift) => shift.preferenceId)

        if (withPreference.length === 0) {
            return
        }

        setBusy(true)

        try {
            for (const shift of withPreference) {
                if ((shift.preferenceReason ?? '') === reason) {
                    continue
                }

                await api.put(`/api/shift-preferences/${shift.preferenceId}`, {
                    type: shift.preferenceType,
                    reason: reason || null,
                })
            }

        } catch {
            setError('שמירת הסיבה נכשלה')
            await loadWeek(true)
        } finally {
            setBusy(false)
        }
    }

    if (loading) {
        return <p className="notice">טוען…</p>
    }

    if (weeks.length === 0) {
        return <p className="notice">אין עדיין שבועות במערכת.</p>
    }

    const overview = employeeId === ALL

    // Disables edit in the all view constraints.
    // if changes are required, manager can select the specific employee
    // and make the changes over there
    const editableStatus = myWeek?.status === 'COLLECTING' || myWeek?.status === 'DRAFT'
    const canEdit = !overview && editableStatus && (myWeek?.submissionOpen || isManager)

    const byShift = new Map()

    for (const preference of allPreferences) {
        const list = byShift.get(preference.shiftId) ?? []
        list.push(preference)
        byShift.set(preference.shiftId, list)
    }

    return (
        <>
            <div className="page-head">
                <h1>אילוצים</h1>
                {isManager && (
                    <select value={employeeId}
                            onChange={(e) => setEmployeeId(
                                e.target.value === ALL ? ALL : Number(e.target.value))}>
                        <option value={ALL}>כל העובדים</option>
                        {employees.map((employee) => (
                            <option key={employee.id} value={employee.id}>
                                {employee.fullName}
                            </option>
                        ))}
                    </select>
                )}
            </div>

            <WeekPicker weeks={weeks} current={week} onChange={setWeek}>
                {myWeek && (
                    <span className="tag-soft">
            {myWeek.submissionOpen ? 'פתוח להגשה' : 'ההגשה נסגרה'}
          </span>
                )}
            </WeekPicker>

            {error && <p className="error">{error}</p>}

            {myWeek && (
                <ShiftGrid
                    shifts={myWeek.shifts}
                    weekStart={myWeek.weekStart}
                    selected={selected}
                    onSelectionChange={canEdit ? setSelected : undefined}
                    renderCell={(shift) => (
                        overview
                            ? <ShiftConstraints
                                preferences={byShift.get(shift.shiftId) ?? []} />
                            : (
                                <div className={`pref pref-${shift.preferenceType ?? 'none'}`}>
                                    <span>{LABELS[shift.preferenceType] ?? 'יכול'}</span>
                                    {shift.preferenceReason && (
                                        <span className="pref-reason" title={shift.preferenceReason}>
                                             {shift.preferenceReason}
                                    </span>
                                    )}
                                </div>
                            )
                    )}
                />
            )}

            {canEdit && selected.size > 0 && (
                <SelectionPanel
                    shifts={selectedShifts}
                    busy={busy}
                    onType={applyType}
                    onReason={applyReason}
                />
            )}

            {overview && (
                <p className="hint">
                    מוצגים האילוצים שהוגשו לשבוע זה. לעריכה יש לבחור עובד.
                </p>
            )}
            {canEdit && selected.size === 0 && (
                <p className="hint">
                    בחר משמרת או גרור על כמה כדי לקבוע אילוץ.
                </p>
            )}
        </>
    )
}

function SelectionPanel({ shifts, busy, onType, onReason }) {
    const [reason, setReason] = useState(sharedReason(shifts))


    const reasonRef = useRef(reason)
    const shiftsRef = useRef(shifts)

    useEffect(() => {
        reasonRef.current = reason
        shiftsRef.current = shifts
    }, [reason, shifts])

    useEffect(() => {
        return () => {
            const current = reasonRef.current
            const target = shiftsRef.current

            if (current !== sharedReason(target)) {
                onReason(current.trim(), target)
            }
        }
    }, [])


    // A new selection brings its own reason, so the field follows it.
    useEffect(() => {
        setReason(sharedReason(shifts))
    }, [shifts.map((shift) => shift.shiftId).join()])

    const current = sharedType(shifts)
    const anyWithPreference = shifts.some((shift) => shift.preferenceId)

    return (
        <div className="panel">
            <div className="panel-head">
                <strong>
                    {shifts.length === 1
                        ? describe(shifts[0])
                        : `${shifts.length} משמרות נבחרו`}
                </strong>
            </div>
            <div className="panel-choices">
                {TYPES.map((type) => (
                    <button
                        key={type.label}
                        disabled={busy}
                        className={current === type.value ? 'choice is-current' : 'choice'}
                        onClick={() => onType(type.value)}
                    >
                        {type.label}
                    </button>
                ))}
            </div>

            {anyWithPreference && (
                <label className="panel-reason">
                    <span className="field-label">סיבה</span>
                    <input
                        value={reason}
                        disabled={busy}
                        maxLength={255}
                        placeholder="רשות"
                        onChange={(e) => setReason(e.target.value)}
                    />
                </label>
            )}
        </div>
    )
}

function describe(shift) {
    const [year, month, day] = shift.shiftDate.split('-')
    return `${shift.shiftTypeName} · ${Number(day)}/${Number(month)}/${year}`
}

// Only shows a type as current when every selected shift agrees.
function sharedType(shifts) {
    const first = shifts[0]?.preferenceType ?? null

    return shifts.every((shift) => (shift.preferenceType ?? null) === first)
        ? first
        : undefined
}

function sharedReason(shifts) {
    const first = shifts[0]?.preferenceReason ?? ''

    return shifts.every((shift) => (shift.preferenceReason ?? '') === first) ? first : ''
}

// Shows the constraints entered by all employees of thw week.
// Reasoning is that manager should have an all out view for the constaints
// and their reasoning to make sure nothing stands out and he is allowed to continue
// with building the schedule
function ShiftConstraints({ preferences }) {
    if (preferences.length === 0) {
        return null
    }

    return (
        <div className="constraint-list">
            {preferences.map((preference) => (
                <div key={preference.id}
                     className={`constraint constraint-${preference.type}`}>
                    <span className="constraint-name">{preference.employeeName}</span>
                    {preference.reason && (
                        <span className="constraint-reason" title={preference.reason}>
                                {preference.reason}
                        </span>
                    )}
                </div>
            ))}
        </div>
    )
}
