import { useEffect, useState } from 'react'
import { api } from '../api/client'
import ShiftGrid from '../components/ShiftGrid'
import WeekPicker from '../components/WeekPicker'

export default function MySchedulePage() {
    const [weeks, setWeeks] = useState([])
    const [week, setWeek] = useState(null)
    const [roster, setRoster] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        api.get('/api/schedules')
            .then((list) => {
                setWeeks(list)
                setWeek(list[0] ?? null)
            })
            .catch(() => setError('לא הצלחנו לטעון את רשימת השבועות'))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => {
        if (!week) {
            return
        }

        api.get(`/api/schedules/${week.id}/roster`)
            .then(setRoster)
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

            {roster && !roster.published && (
                <p className="notice">הסידור לשבוע זה טרם פורסם.</p>
            )}

            {roster?.published && (
                <ShiftGrid
                    shifts={roster.shifts}
                    weekStart={roster.weekStart}
                    renderCell={(shift) => (
                        <div className={shift.assignedToMe ? 'cell-mine' : ''}>
                            {shift.assignments.length === 0 && <span className="cell-empty">—</span>}

                            {shift.assignments.map((person) => (
                                <div key={person.employeeId}
                                     className={person.isMe ? 'person person-me' : 'person'}>
                                    {person.fullName}
                                </div>
                            ))}
                        </div>
                    )}
                />
            )}
        </>
    )
}
