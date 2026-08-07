// A labelled input. Every form here is the same shape, so this keeps the
// markup out of them.
export default function Field({ label, error, children }) {
    return (
        <label className="field">
            <span className="field-label">{label}</span>
            {children}
            {error && <span className="field-error">{error}</span>}
        </label>
    )
}
