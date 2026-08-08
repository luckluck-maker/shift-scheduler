import { useEffect, useState } from 'react'
import { api } from '../api/client'
import ShiftGrid from '../components/ShiftGrid'
import WeekPicker from '../components/WeekPicker'
import ShiftCell from '../components/ShiftCell'
import Field from '../components/Field'
import PublishDialog from '../components/PublishDialog'

const STATUS_LABELS = {
    COLLECTING: 'פתוח להגשת אילוצים',
    DRAFT: 'טיוטה',
    PUBLISHED: 'פורסם',
}

export default function ScheduleBuilderPage() {
    const [weeks, setWeeks] = useState([])
    const [week, setWeek] = useState(null)
    const [detail, setDetail] = useState(null)
    const [coverage, setCoverage] = useState([])
    const [positions, setPositions] = useState([])

    const [selected, setSelected] = useState(new Set())
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [busy, setBusy] = useState(false)
    const [publishing, setPublishing] = useState(false)

    useEffect(() => {
        Promise.all([api.get('/api/schedules'), api.get('/api/job-positions')])
            .then(([weekList, positionList]) => {
                setWeeks(weekList)
                setWeek(weekList[0] ?? null)
                setPositions(positionList)
            })
            .catch(() => setError('לא הצלחנו לטעון את הנתונים'))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => {
        if (week) {
            loadWeek()
        }
    }, [week])

    async function loadWeek(keepSelection = false) {
        if (!keepSelection) {
            setSelected(new Set())
        }

        try {
            const [detailData, coverageData] = await Promise.all([
                api.get(`/api/schedules/${week.id}`),
                api.get(`/api/schedules/${week.id}/coverage`),
            ])

            setDetail(detailData)
            setCoverage(coverageData)
            setError(null)
        } catch {
            setError('לא הצלחנו לטעון את הסידור')
        }
    }

    // Escape and clicking away clear the selection, the same as on the
    // constraints screen.
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

    async function createWeek() {
        const next = weeks.length === 0
            ? nextSunday()
            : addDays(weeks[0].weekStart, 7)

        setBusy(true)

        try {
            const created = await api.post('/api/schedules', { weekStart: next })
            const list = await api.get('/api/schedules')

            setWeeks(list)
            setWeek(list.find((candidate) => candidate.id === created.id) ?? list[0])
        } catch (err) {
            setError(err.status === 409
                ? 'כבר קיים סידור לשבוע הזה'
                : 'יצירת השבוע נכשלה')
        } finally {
            setBusy(false)
        }
    }

    // Requirements are replaced per shift, so applying to a selection means one
    // call each. They all send the same version, which is fine - the server only
    // bumps it once per request and a stale one would be rejected loudly.
    async function applyRequirements(counts) {
        setBusy(true)

        try {
            const body = Object.entries(counts)
                .map(([jobPositionId, requiredCount]) => ({
                    jobPositionId: Number(jobPositionId),
                    requiredCount: Number(requiredCount) || 0,
                }))

            let version = detail.version

            for (const shiftId of selected) {
                const updated = await api.put(
                    `/api/schedules/${week.id}/shifts/${shiftId}/requirements`,
                    { version, requirements: body })

                version = updated.scheduleVersion ?? version + 1
            }

            await loadWeek(true)
        } catch (err) {
            setError(err.code === 'STALE_VERSION'
                ? 'הסידור השתנה במקביל. רענן ונסה שוב'
                : 'שמירת הדרישות נכשלה')
        } finally {
            setBusy(false)
        }
    }

    async function lock() {
        await act(() => api.put(`/api/schedules/${week.id}/lock`,
            { version: detail.version }))
    }

    async function publish() {
        await act(() => api.put(`/api/schedules/${week.id}/publish`,
            { version: detail.version }))
    }

    async function act(call) {
        setBusy(true)

        try {
            await call()
            await loadWeek()

            setWeeks(await api.get('/api/schedules'))
        } catch (err) {
            setError(messageFor(err))
        } finally {
            setBusy(false)
        }
    }

    if (loading) {
        return <p className="notice">טוען…</p>
    }

    const byShiftId = new Map(coverage.map((entry) => [entry.shiftId, entry]))
    const collecting = detail?.status === 'COLLECTING'

    return (
        <>
            <div className="page-head">
                <h1>בניית סידור</h1>

                <div className="head-actions">
                    {collecting && (
                        <button onClick={lock} disabled={busy}>סגירת הגשת אילוצים</button>
                    )}

                    {detail?.status === 'DRAFT' && (
                        <button onClick={() => setPublishing(true)} disabled={busy}>פרסום</button>
                    )}

                    <button className="secondary" onClick={createWeek} disabled={busy}>
                        שבוע חדש
                    </button>
                </div>
            </div>

            {weeks.length === 0 ? (
                <p className="notice">אין עדיין שבועות. התחל בלחיצה על "שבוע חדש".</p>
            ) : (
                <>
                    <WeekPicker weeks={weeks} current={week} onChange={setWeek}>
                        <span className="tag-soft">{STATUS_LABELS[detail?.status] ?? ''}</span>
                    </WeekPicker>

                    {error && <p className="error">{error}</p>}

                    {detail && (
                        <ShiftGrid
                            shifts={detail.shifts.map(toGridShift)}
                            weekStart={detail.weekStart}
                            selected={selected}
                            onSelectionChange={detail.status === 'PUBLISHED' ? undefined : setSelected}
                            renderCell={(shift) => (
                                <ShiftCell coverage={byShiftId.get(shift.shiftId)} collecting={collecting} />
                            )}
                        />
                    )}

                    {selected.size > 0 && (
                        <RequirementsPanel
                            shifts={detail.shifts.filter((shift) => selected.has(shift.id))}
                            positions={positions}
                            busy={busy}
                            onApply={applyRequirements}
                        />
                    )}

                    {selected.size === 0 && detail?.status !== 'PUBLISHED' && (
                        <p className="hint">בחר משמרת או גרור על כמה כדי לקבוע דרישות איוש.</p>
                    )}
                </>
            )}
            {publishing && (
                <PublishDialog
                    coverage={coverage}
                    busy={busy}
                    onClose={() => setPublishing(false)}
                    onConfirm={async () => {
                        await publish()
                        setPublishing(false)
                    }}
                />
            )}
        </>
    )
}

function RequirementsPanel({ shifts, positions, busy, onApply }) {
    const [counts, setCounts] = useState({})

    // A new selection brings its own numbers. Where the selected shifts disagree
    // the field is left blank rather than showing one of them.
    useEffect(() => {
        const next = {}

        for (const position of positions) {
            const values = shifts.map((shift) => requiredFor(shift, position.id))
            const first = values[0]

            next[position.id] = values.every((value) => value === first) ? first : ''
        }

        setCounts(next)
    }, [shifts.map((shift) => shift.id).join(), positions])

    return (
        <div className="panel">
            <div className="panel-head">
                <strong>
                    {shifts.length === 1
                        ? describe(shifts[0])
                        : `${shifts.length} משמרות נבחרו`}
                </strong>
            </div>

            <div className="requirement-fields">
                {positions.map((position) => (
                    <Field key={position.id} label={position.name}>
                        <input
                            type="number"
                            min={0}
                            max={50}
                            value={counts[position.id] ?? ''}
                            disabled={busy}
                            onChange={(e) =>
                                setCounts((current) => ({ ...current, [position.id]: e.target.value }))}
                        />
                    </Field>
                ))}
            </div>

            <div className="form-actions">
                <button onClick={() => onApply(counts)} disabled={busy}>
                    {busy ? 'שומר…' : 'שמירת דרישות'}
                </button>
            </div>
        </div>
    )
}

// The grid works in the shape my-week uses, so the manager's detail response
// is mapped onto it rather than the grid learning a second one.
function toGridShift(shift) {
    return {
        shiftId: shift.id,
        shiftDate: shift.shiftDate,
        shiftTypeName: shift.shiftTypeName,
        startTime: shift.startTime,
        endTime: shift.endTime,
    }
}

function requiredFor(shift, jobPositionId) {
    const found = shift.requirements.find(
        (requirement) => requirement.jobPositionId === jobPositionId)

    return found ? String(found.requiredCount) : '0'
}

function describe(shift) {
    const [year, month, day] = shift.shiftDate.split('-')
    return `${shift.shiftTypeName} · ${Number(day)}/${Number(month)}/${year}`
}

function nextSunday() {
    const date = new Date()
    date.setDate(date.getDate() + ((7 - date.getDay()) % 7 || 7))
    return date.toISOString().slice(0, 10)
}

function addDays(iso, days) {
    const date = new Date(iso)
    date.setDate(date.getDate() + days)
    return date.toISOString().slice(0, 10)
}

function messageFor(error) {
    if (error.code === 'STALE_VERSION') {
        return 'הסידור השתנה במקביל. רענן ונסה שוב'
    }

    if (error.code === 'WRONG_STATUS') {
        return 'הפעולה אינה אפשרית במצב הנוכחי של הסידור'
    }

    return 'הפעולה נכשלה'
}
