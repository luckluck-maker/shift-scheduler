// One cell of the manager's grid: a row per person the shift asks for, filled
// or not.
//
// While the week is collecting nobody should be assigned yet, so a gap isn't a
// problem and the cell just says what's wanted. From the draft on, gaps are
// what the manager is here to close, so they're coloured:
// amber for a position that's short.
// red for one with nobody in it at all.
// Everything else stays plain.
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

    return (
        <div className="slots">
            {coverage.positions.map((position) => (
                <PositionSlots
                    key={position.jobPositionId}
                    position={position}
                    people={peopleFor(coverage, position.jobPositionId)}
                />
            ))}

            {extras(coverage).map((person) => (
                <div key={person.id} className="slot slot-extra">
                    {person.employeeName}
                    <span className="slot-mark">+</span>
                </div>
            ))}
        </div>
    )
}

// Three names is about all that fits before the row gets too tall to scan, so
// the rest become a count. The panel has the full list.
const MAX_NAMES = 3

function PositionSlots({ position, people }) {
    const empty = Math.max(0, position.required - people.length)
    const shown = people.slice(0, MAX_NAMES)
    const hidden = people.length - shown.length

    const className = people.length === 0 && position.required > 0
        ? 'slot-group is-deserted'
        : 'slot-group'

    return (
        <div className={className}>
            {shown.map((person) => (
                <div key={person.id} className="slot">{person.employeeName}</div>
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
        (assignment) => !assignment.override
            && assignment.jobPositionId === jobPositionId)
}

// Assignments the shift never asked for, so they sit under the positions
// rather than inside one.
function extras(coverage) {
    return coverage.assignments.filter((assignment) => assignment.override)
}
