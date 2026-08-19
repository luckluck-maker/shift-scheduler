import { useEffect, useState } from 'react'

const DAY_NAMES = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת']

// The week laid out as a table: a row per shift type, a column per day.
//
// The rows come from the shifts this week actually has, not from every type
// ever defined. They're sorted by start time so the day reads top to bottom.
//
// What shows in the cells is up to the caller. The same grid shows:
// 1. the roster to everyone
// 2. for employees their own constraints and for a manager someone else's constraints
// 3. for manager's new schedule week the roster plus what's still missing.
export default function ShiftGrid({ shifts, weekStart, renderCell,
                                      selected, onSelectionChange }) {
    const [dragging, setDragging] = useState(false)

    // Turns the grid read-only unless the caller passes a handler for the
    // selection.
    const selectable = Boolean(onSelectionChange)

    // A drag that ends outside the grid still has to stop, so the listener sits
    // on the window rather than on a cell.
    useEffect(() => {
        if (!dragging) {
            return
        }

        function stop() {
            setDragging(false)
        }

        window.addEventListener('mouseup', stop)
        return () => window.removeEventListener('mouseup', stop)
    }, [dragging])

    const rows = shiftTypeRows(shifts)
    const days = weekDays(weekStart)

    // Maps every shift by its type and date, so a cell can find its own from the
    // row and the column.
    const byTypeAndDate = new Map(
        shifts.map((shift) => [`${shift.shiftTypeName}|${shift.shiftDate}`, shift]))

    // Selection is cumulative: dragging or clicking adds cells, and going over
    // one that's already picked takes it out. There's no separate way to clear -
    // you undo a selection the same way you made it.
    function startDrag(shiftId) {
        if (!selectable) {
            return
        }

        setDragging(true)
        toggle(shiftId)
    }

    function extendDrag(shiftId) {
        if (dragging) {
            toggle(shiftId)
        }
    }

    function toggle(shiftId) {
        const next = new Set(selected)

        if (next.has(shiftId)) {
            next.delete(shiftId)
        } else {
            next.add(shiftId)
        }

        onSelectionChange(next)
    }

    return (
        <table className={selectable ? 'grid grid-selectable' : 'grid'}>
            <thead>
            <tr>
                <th className="grid-corner" />
                {days.map((day) => (
                    <th key={day.iso}>
                        <span className="grid-day">{day.name}</span>
                        <span className="grid-date">{day.label}</span>
                    </th>
                ))}
            </tr>
            </thead>
            <tbody>
            {rows.map((row) => (
                <tr key={row.name}>
                    <th className="grid-row-head">
                        <span className="grid-shift-name" title={row.name}>{row.name}</span>
                        <span className="grid-shift-time">{row.startTime}–{row.endTime}</span>
                    </th>

                    {days.map((day) => {
                        const shift = byTypeAndDate.get(`${row.name}|${day.iso}`)

                        // Draws an empty cell when the week has no shift of that
                        // type on that day.
                        if (!shift) {
                            return <td key={day.iso} className="grid-cell grid-cell-none" />
                        }

                        const isSelected = selected?.has(shift.shiftId)

                        return (
                            <td
                                key={day.iso}
                                className={isSelected ? 'grid-cell is-selected' : 'grid-cell'}
                                onMouseDown={() => startDrag(shift.shiftId)}
                                onMouseEnter={() => extendDrag(shift.shiftId)}
                            >
                                {renderCell(shift)}
                            </td>
                        )
                    })}
                </tr>
            ))}
            </tbody>
        </table>
    )
}

function shiftTypeRows(shifts) {
    const byName = new Map()

    for (const shift of shifts) {
        if (!byName.has(shift.shiftTypeName)) {
            byName.set(shift.shiftTypeName, {
                name: shift.shiftTypeName,
                startTime: shift.startTime,
                endTime: shift.endTime,
            })
        }
    }

    return [...byName.values()].sort((a, b) => a.startTime.localeCompare(b.startTime))
}

// Built in UTC so the daylight saving switch can't shift the dates.
function weekDays(weekStart) {
    const [year, month, day] = weekStart.split('-').map(Number)
    return Array.from({ length: 7 }, (unused, offset) => {
        const date = new Date(Date.UTC(year, month - 1, day + offset))

        return {
            iso: date.toISOString().slice(0, 10),
            name: DAY_NAMES[offset],
            label: `${date.getUTCDate()}/${date.getUTCMonth() + 1}`,
        }
    })
}
