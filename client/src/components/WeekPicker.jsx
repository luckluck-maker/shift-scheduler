// Arrows between weeks, newest first.
export default function WeekPicker({ weeks, current, onChange, children }) {
    const index = weeks.findIndex((week) => week.id === current?.id)

    const older = weeks[index + 1]
    const newer = weeks[index - 1]

    return (
        <div className="week-picker">
            <button className="icon-button" disabled={!older}
                    onClick={() => onChange(older)}>‹</button>

            <div className="week-label">
                <strong>{formatRange(current)}</strong>
                {children}
            </div>

            <button className="icon-button" disabled={!newer}
                    onClick={() => onChange(newer)}>›</button>
        </div>
    )
}

function formatRange(week) {
    if (!week) {
        return ''
    }

    return `${formatDate(week.weekStart)} – ${formatDate(week.weekEnd)}`
}

function formatDate(iso) {
    const [, month, day] = iso.split('-')
    return `${Number(day)}/${Number(month)}`
}
