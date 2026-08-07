import { useEffect } from 'react'

// Used for the add/edit forms and later for the assignment warning.
export default function Modal({ title, onClose, children }) {
    // Escape closes it. The cleanup matters - without it every open modal
    // would leave a listener behind.
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
