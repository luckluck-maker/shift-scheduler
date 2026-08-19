// Wraps a label and an input together, since every form here is the same shape.
export default function Field({ label, children }) {
    return (
        <label className="field">
            <span className="field-label">{label}</span>
            {children}
        </label>
    )
}
