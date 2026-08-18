import { useEffect, useState } from 'react'
import { api } from '../api/client'
import ShiftGrid from '../components/ShiftGrid'
import WeekPicker from '../components/WeekPicker'

// The roster for one week, as an employee sees it.
export default function MySchedulePage() {
    const [weeks, setWeeks] = useState([])
    const [week, setWeek] = useState(null)
    const [roster, setRoster] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        api.get('/api/schedules')
            .then((list) => {
                // Starts on the latest published week. The list comes back newest
                // first, so that is the first one.
                const published = list.filter((week) => week.status === 'PUBLISHED')
                setWeeks(list)
                // Nothing published yet, so it opens on whatever week exists.
                setWeek(published[0] ?? list[0] ?? null)
            })
            .catch(() => setError('לא הצלחנו לטעון את רשימת השבועות'))
            .finally(() => setLoading(false))
    }, [])

    // Reloads whenever the picker moves to another week.
    useEffect(() => {
        if (!week) {
            return
        }

        api.get(`/api/schedules/${week.id}/roster`)
            .then((data) => {
                setRoster(data)
                setError(null)
            })
            .catch(() => setError('לא הצלחנו לטעון את הסידור'))
    }, [week])

    if (loading) {
        return <p className="notice">טוען…</p>
    }

    if (weeks.length === 0) {
        return <p className="notice">אין עדיין שבועות במערכת.</p>
    }

    return (
        <>
            <div className="page-head">
                <h1>הסידור</h1>
            </div>

            <WeekPicker weeks={weeks} current={week} onChange={setWeek} />

            {error && <p className="error">{error}</p>}

            {/* The picker lists every week, including ones still being planned. The
                server sends no shifts for those, so the screen says it isn't out yet. */}
            {roster && !roster.published && (
                <p className="notice">הסידור לשבוע זה טרם פורסם.</p>
            )}

            {roster?.published && (
                <ShiftGrid
                    shifts={roster.shifts}
                    weekStart={roster.weekStart}
                    renderCell={(shift) => (
                        shift.assignments.length === 0 ? (
                            <div className="cell-empty" />
                        ) : (
                            // Two flags: one marks the whole cell as a shift I'm on,
                            // the other marks my name inside it.
                            <div className={shift.assignedToMe ? 'cell-mine' : ''}>
                                {shift.assignments.map((person) => (
                                    <div key={person.employeeId}
                                         title={person.fullName}
                                         className={person.isMe ? 'person person-me' : 'person'}>
                                        {person.fullName}
                                    </div>
                                ))}
                            </div>
                        )
                    )}
                />
            )}
        </>
    )
}
