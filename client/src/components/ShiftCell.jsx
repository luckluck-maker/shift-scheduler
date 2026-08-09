// One cell of the manager's grid: a row per person the shift requirement, filled
// or not.
//
// While the week is collecting nobody should be assigned yet, so a gap isn't a
// problem and the cell just says what's wanted. From the draft on, gaps are highlighted
// to allow the manager to visually see what he needs to close:
// amber for a position that's short.
// red for an essential position with nobody in it at all.
// green for a position that has been filled.
export default function ShiftCell({ coverage, collecting }) {
    if (!coverage || coverage.positions.length === 0) {
        return <span className="slot-none">—</span>
    }

    if (collecting) {
        return (
            <div className="slots">
                {coverage.positions.map((position) => (
                    <div key={position.jobPositionId} className="slot-wanted">
                        {position.required} {position.jobPositionName}
                    </div>
                ))}
            </div>
        )
    }

    const showLabels = coverage.positions.length > 1

    return (
        <div className="slots">
            {coverage.positions.map((position) => (
                <PositionSlots
                    key={position.jobPositionId}
                    position={position}
                    people={peopleFor(coverage, position.jobPositionId)}
                    showLabel={showLabels}
                />
            ))}

            {unrequested(coverage).map((person) => (
                <div key={person.id} className="slot-group">
                    {showLabels && <div className="slot-position">{person.jobPositionName}</div>}
                    <div className="slot slot-extra">
                        {person.employeeName}
                        <span className="slot-mark">+</span>
                    </div>
                </div>
            ))}
        </div>
    )
}

// Three names is about all that fits before the row gets too tall to scan, so
// the rest become a count. The panel below has the full list.
const MAX_NAMES = 3

function PositionSlots({ position, people, showLabel }) {
    const empty = Math.max(0, position.required - people.length)

    // Beyond the shift requirement for the position - either the
    // manager assigned over the requirement, or lowered it afterward.
    const needed = Math.min(people.length, position.required)

    const shown = people.slice(0, MAX_NAMES)
    const hidden = people.length - shown.length

    // Only an essential position that is completely empty turns red
    // as per the logic defined in the solver rules
    const deserted = position.essential
        && people.length === 0
        && position.required > 0

    return (
        <div className={deserted ? 'slot-group is-deserted' : 'slot-group'}>
            {showLabel && (
                <div className="slot-position">
                    <span>{position.jobPositionName}</span>
                    <span className="slot-count">{people.length}/{position.required}</span>
                </div>
            )}
            {shown.map((person, index) => (
                <div key={person.id}
                     className={index >= needed ? 'slot slot-extra' : 'slot'}>
                    {person.employeeName}
                    {index >= needed && <span className="slot-mark">+</span>}
                </div>
            ))}

            {hidden > 0 && <div className="slot slot-more">+{hidden}</div>}

            {Array.from({ length: empty }, (unused, index) => (
                <div key={index} className="slot slot-empty" />
            ))}
        </div>
    )
}


function peopleFor(coverage, jobPositionId) {
    return coverage.assignments.filter(
        (assignment) => assignment.jobPositionId === jobPositionId)
}

// Assignment of a position this shift has no requirement for at all. They
// have no group to sit in, so they go underneath.
function unrequested(coverage) {
    const wanted = new Set(
        coverage.positions.map((position) => position.jobPositionId))

    return coverage.assignments.filter(
        (assignment) => !wanted.has(assignment.jobPositionId))
}
