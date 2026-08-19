import Modal from './Modal'

// Pop up confirmation before publishing a schedule.
// Includes the warnings to make sure the manager is presented with
// all the relevant information before publishing.
export default function PublishDialog({ coverage, busy, onConfirm, onClose }) {
    // Counts the shifts that are missing anyone at all.
    const short = coverage.filter((shift) =>
        shift.positions.some((position) => position.missing > 0))

    // Collects the essential positions that nobody was assigned to, listed once
    // even when several shifts are short.
    const deserted = coverage.flatMap((shift) =>
        shift.positions
            .filter((position) => position.essential
                && position.required > 0
                && position.assigned === 0)
            .map((position) => position.jobPositionName))

    const desertedNames = [...new Set(deserted)]

    return (
        <Modal title="פרסום הסידור" onClose={onClose}>
            <div className="dialog">
                {short.length === 0 ? (
                    <p>כל המשמרות מאוישות במלואן.</p>
                ) : (
                    <ul className="warnings">
                        <li>{short.length} משמרות אינן מאוישות במלואן</li>

                        {desertedNames.length > 0 && (
                            <li>
                                יש משמרות ללא אף {desertedNames.join(' / ')}
                            </li>
                        )}
                    </ul>
                )}

                <p className="hint">
                    לאחר הפרסום העובדים יקבלו הודעה במייל, ולא יהיה אפשרי להריץ שוב
                    את בניית הסידור האוטומטית. שינויים יהיו אפשריים בשיבוץ ידני בלבד,
                    ובסיומם ניתן לפרסם מחדש כדי להודיע למי שהושפע.
                </p>

                <div className="form-actions">
                    <button type="button" className="link-button" onClick={onClose}>ביטול</button>
                    <button onClick={onConfirm} disabled={busy}>
                        {busy ? 'מפרסם…' : 'פרסום'}
                    </button>
                </div>
            </div>
        </Modal>
    )
}
