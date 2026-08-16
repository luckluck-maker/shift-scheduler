import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { ruleText, isBlocking, RULE_CONSEQUENCE } from '../i18n/Rules.js'
import Modal from './Modal'

// Made possible only for single shift selection.

// The list contains all the employees to allow the following:
// 1. Ensure the manager has full view of his employees
// 2. Allows the manager to view who can't be assigned and why
// 3. Allows the manager to assign someone who isn't required per shift requirement
export default function AssignmentPanel({ shift, coverage, scheduleVersion, onChanged, onError }) {
    const [candidates, setCandidates] = useState([])
    const [chosen, setChosen] = useState('')
    const [busy, setBusy] = useState(false)
    const [pending, setPending] = useState(null)

    useEffect(() => {
        setChosen('')

        api.get(`/api/shifts/${shift.id}/available-employees`)
            .then(setCandidates)
            .catch(() => onError('לא הצלחנו לטעון את רשימת העובדים'))
    }, [shift.id, coverage])

    async function assign(override) {
        setBusy(true)

        try {
            await api.post('/api/assignments', {
                shiftId: shift.id,
                employeeId: Number(chosen),
                override,
                scheduleVersion,
            })

            setPending(null)
            setChosen('')
            onChanged()
        } catch (err) {
            handleFailure(err)
        } finally {
            setBusy(false)
        }
    }

    // A rejection carries the rules that were broken, so the dialog is built
    // from the response.
    function handleFailure(err) {
        const body = err.body

        if (err.status !== 409 || !body?.overridable) {
            onError('השיבוץ נכשל')
            return
        }

        if (body.blocking?.length > 0) {
            onError(body.blocking.map((violation) => ruleText(violation.rule)).join(' · '))
            return
        }

        setPending(body.overridable)
    }

    async function remove(assignmentId) {
        setBusy(true)

        try {
            await api.delete(`/api/assignments/${assignmentId}?version=${scheduleVersion}`)
            onChanged()
        } catch {
            onError('ההסרה נכשלה')
        } finally {
            setBusy(false)
        }
    }

    const assigned = coverage?.assignments ?? []

    return (
        <>
            <div className="assigned">
                <span className="field-label">משובצים</span>

                {assigned.length === 0 ? (
                    <p className="hint">אף אחד עדיין לא שובץ למשמרת זו.</p>
                ) : (
                    <ul className="assigned-list">
                        {sortByPosition(coverage).map((assignment, index, list) => (
                            <li key={assignment.id}>
                                <span>{assignment.employeeName}</span>
                                <span className="cell-muted">{assignment.jobPositionName}</span>
                                {isExtra(assignment, list, coverage) && (
                                    <span className="tag-soft">נוסף</span>
                                )}
                                <button className="icon-remove" disabled={busy}
                                        aria-label={`הסרת ${assignment.employeeName}`}
                                        onClick={() => remove(assignment.id)}>×</button>
                            </li>
                        ))}
                    </ul>
                )}
            </div>


            <div className="assign-row">
                <select value={chosen} disabled={busy}
                        onChange={(e) => setChosen(e.target.value)}>
                    <option value="" disabled hidden>הוספת עובד…</option>

                    {group(candidates).map(([position, people]) => (
                        <optgroup key={position} label={position}>
                            {people.map((person) => (
                                <option key={person.employeeId} value={person.employeeId}
                                        disabled={isBlocking(person.violatedRule)}>
                                    {label(person)}
                                </option>
                            ))}
                        </optgroup>
                    ))}
                </select>

                <button disabled={busy || !chosen} onClick={() => assign(false)}>שיבוץ</button>
            </div>

            {pending && (
                <OverrideDialog
                    violations={pending}
                    busy={busy}
                    onClose={() => setPending(null)}
                    onConfirm={() => assign(true)}
                />
            )}
        </>
    )
}

// Blocked people stay on the list, greyed out.
function label(person) {
    return person.violatedRule
        ? `${person.fullName} · ${ruleText(person.violatedRule)}`
        : person.fullName
}

function group(candidates) {
    const byPosition = new Map()

    for (const person of candidates) {
        const list = byPosition.get(person.jobPositionName) ?? []
        list.push(person)
        byPosition.set(person.jobPositionName, list)
    }

    return [...byPosition.entries()]
}

function OverrideDialog({ violations, busy, onClose, onConfirm }) {
    return (
        <Modal title="אישור שיבוץ" onClose={onClose}>
            <ul className="warnings">
                {violations.map((violation) => (
                    <li key={violation.rule}>
                        {ruleText(violation.rule)}
                        {RULE_CONSEQUENCE[violation.rule] && (
                            <span className="consequence">{RULE_CONSEQUENCE[violation.rule]}</span>
                        )}
                    </li>
                ))}
            </ul>

            <div className="form-actions">
                <button type="button" className="link-button" onClick={onClose}>ביטול</button>
                <button onClick={onConfirm} disabled={busy}>שיבוץ בכל זאת</button>
            </div>
        </Modal>
    )
}

// Same order as the grid, so the two can be read against each other. Anyone
// holding a position the shift never asked for goes last, as they do there.
function sortByPosition(coverage) {
    const order = coverage.positions.map((position) => position.jobPositionId)

    return [...coverage.assignments].sort((a, b) => {
        const left = order.indexOf(a.jobPositionId)
        const right = order.indexOf(b.jobPositionId)

        return (left === -1 ? order.length : left) - (right === -1 ? order.length : right)
    })
}

// Somebody the shift doesn't ask for. Counted rather than read off the
// assignment: lowering a requirement after people were assigned leaves the
// flag alone, and the grid counts the same way.
function isExtra(assignment, list, coverage) {
    const position = coverage.positions.find(
        (candidate) => candidate.jobPositionId === assignment.jobPositionId)

    if (!position) {
        return true
    }

    const samePosition = list.filter(
        (candidate) => candidate.jobPositionId === assignment.jobPositionId)

    return samePosition.indexOf(assignment) >= position.required
}
