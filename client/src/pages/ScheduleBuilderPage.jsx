import { useEffect, useState } from 'react'
import { api } from '../api/client'
import ShiftGrid from '../components/ShiftGrid'
import WeekPicker from '../components/WeekPicker'
import ShiftCell from '../components/ShiftCell'
import Field from '../components/Field'
import PublishDialog from '../components/PublishDialog'
import AssignmentPanel from '../components/AssignmentPanel'

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

    const [solving, setSolving] = useState(false)
    const [solveResult, setSolveResult] = useState(null)

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
            void loadWeek()
        }
    }, [week])

    async function loadWeek(keepSelection = false) {
        if (!keepSelection) {
            setSelected(new Set())
        }

        setSolveResult(null)

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
                .map(([jobPositionId, value]) => ({
                    jobPositionId: Number(jobPositionId),
                    requiredCount: Number(value.count) || 0,
                    essential: value.essential,
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

    // The request comes back immediately and the solver carries on in the
    // background, so the screen asks every second whether it's finished. Since timefold
    // can backtrack and unassign employees, not showing assignment progress.
    // Instead, shows loading / working to show that the solver is currently working.
    async function solve() {
        setSolving(true)
        setSolveResult(null)
        setError(null)

        try {
            await api.post(`/api/schedules/${week.id}/solve`)
            await waitForSolver()

            const fresh = await api.get(`/api/schedules/${week.id}/coverage`)

            setCoverage(fresh)
            setDetail(await api.get(`/api/schedules/${week.id}`))
            setSolveResult(summarise(fresh))
        } catch (err) {
            setError(messageFor(err))
        } finally {
            setSolving(false)
        }
    }

    // NOT_SOLVING comes back both before the solver has picked the job up and
    // after it's done, so the first check waits a moment. Without that the
    // screen would decide it had finished before it started.
    async function waitForSolver() {
        await pause(700)

        for (let attempt = 0; attempt < 120; attempt++) {
            const status = await api.get(`/api/schedules/${week.id}/solve-status`)

            if (!status.solving) {
                return
            }

            await pause(1000)
        }

        throw new Error('Solver did not finish in time')
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

    const selectedShifts = detail
        ? detail.shifts.filter((shift) => selected.has(shift.id))
        : []

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
                    <div className="week-row">
                        <WeekPicker weeks={weeks} current={week} onChange={setWeek}>
                            <span className="week-status">{STATUS_LABELS[detail?.status] ?? ''}</span>
                        </WeekPicker>

                        {detail?.status === 'DRAFT' && (
                            <button className="solve-button" disabled={solving || busy}
                                    onClick={solve}>
                                {solving ? 'בונה סידור…' : 'בנייה אוטומטית'}
                            </button>
                        )}
                    </div>

                    {solveResult && (
                        <p className={`solve-result ${severity(solveResult)}`}>
                            הסידור נבנה · {solveResult.filled} שיבוצים

                            {solveResult.missing === 0 && ' · כל המשמרות מאוישות'}

                            {solveResult.missing > 0
                                && ` · ${solveResult.missing} מקומות נותרו חסרים`}

                            {solveResult.deserted > 0
                                && ` · ${solveResult.deserted} תפקידים ללא איוש כלל`}
                        </p>
                    )}

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
                        <div className="panel">
                            <div className="panel-head">
                                <strong>
                                    {selected.size === 1
                                        ? describe(selectedShifts[0])
                                        : `${selected.size} משמרות נבחרו`}
                                </strong>
                            </div>

                            {selected.size === 1 && detail.status !== 'COLLECTING' && (
                                <AssignmentPanel
                                    shift={selectedShifts[0]}
                                    coverage={byShiftId.get(selectedShifts[0].id)}
                                    onChanged={() => loadWeek(true)}
                                    onError={setError}
                                />
                            )}

                            {detail.status !== 'PUBLISHED' && (
                                <RequirementFields
                                    shifts={selectedShifts}
                                    positions={positions}
                                    busy={busy}
                                    onApply={applyRequirements}
                                />
                            )}
                        </div>
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
// Each position carries a count and whether the shift can run without it.
function RequirementFields({ shifts, positions, busy, onApply }) {
    const [values, setValues] = useState({})

    // A new selection brings its own numbers. Where the selected shifts differ
    // the field is left blank rather than showing one of them.
    useEffect(() => {
        const next = {}

        for (const position of positions) {
            const found = shifts.map((shift) => requirementFor(shift, position.id))

            const counts = found.map((requirement) => requirement.count)
            const flags = found.map((requirement) => requirement.essential)

            next[position.id] = {
                count: counts.every((count) => count === counts[0]) ? counts[0] : '',
                essential: flags.every((flag) => flag === flags[0]) ? flags[0] : true,
            }
        }

        setValues(next)
    }, [shifts.map((shift) => shift.id).join(), positions])

    function set(positionId, changes) {
        setValues((current) => ({
            ...current,
            [positionId]: { ...current[positionId], ...changes },
        }))
    }

    return (
        <div className="requirements">
            <span className="field-label">דרישות איוש</span>

            <div className="requirement-fields">
                {positions.map((position) => (
                    <div key={position.id} className="requirement-field">
                        <Field label={position.name}>
                            <input
                                type="number"
                                min={0}
                                max={50}
                                value={values[position.id]?.count ?? ''}
                                disabled={busy}
                                onChange={(e) => set(position.id, { count: e.target.value })}
                            />
                        </Field>

                        <label className="checkbox">
                            <input
                                type="checkbox"
                                checked={values[position.id]?.essential ?? true}
                                disabled={busy || !Number(values[position.id]?.count)}
                                onChange={(e) => set(position.id, { essential: e.target.checked })}
                            />
                            חיוני
                        </label>
                    </div>
                ))}
            </div>

            <p className="hint hint-quiet">
                * תפקיד חיוני שנשאר ללא איוש כלל מסומן באדום, והמנוע יעדיף לאייש אותו
                על פני תפקיד שאינו חיוני.
            </p>

            <div className="form-actions">
                <button onClick={() => onApply(values)} disabled={busy}>
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

function requirementFor(shift, jobPositionId) {
    const found = shift.requirements.find(
        (requirement) => requirement.jobPositionId === jobPositionId)

    return found
        ? { count: String(found.requiredCount), essential: found.essential }
        : { count: '0', essential: true }
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

function pause(millis) {
    return new Promise((resolve) => setTimeout(resolve, millis))
}

// Summary of the solver work - assigns and unassigned.
function summarise(coverage) {
    let filled = 0
    let missing = 0
    let deserted = 0

    for (const shift of coverage) {
        for (const position of shift.positions) {
            filled += position.assigned
            missing += position.missing

            if (position.essential && position.required > 0 && position.assigned === 0) {
                deserted++
            }
        }
    }

    return { filled, missing, deserted }
}

function severity(result) {
    if (result.deserted > 0) {
        return 'is-deserted'
    }

    return result.missing > 0 ? 'has-gaps' : ''
}