import { useEffect } from 'react'

// Used for the add/edit forms and later for the assignment warning.
export default function Modal({ title, onClose, children }) {
    // Closes on Escape, and removes the listener when the modal goes away.
    useEffect(() => {
        function onKey(event) {
            if (event.key === 'Escape') {
                onClose()
            }
        }

        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [onClose])

    return (
        // Closes when the backdrop is clicked, and stops a click inside the modal
        // from reaching it.
        <div className="modal-backdrop" onClick={onClose}>
            <div className="modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-head">
                    <h2>{title}</h2>
                    <button className="icon-button" onClick={onClose}>×</button>
                </div>
                {children}
            </div>
        </div>
    )
}
