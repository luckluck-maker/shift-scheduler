// The browser writes its own message for required, minLength, type="email"
// and the number ranges, in the browser's language. These replace it.
//
// The message has to be set before the browser checks the field, so it is set
// while the user types and again when the form is submitted.
//
// Only failures the fields can reach are here. Nothing uses pattern, and
// maxLength blocks the typing instead of failing.

const MESSAGES = [
    ['valueMissing', () => 'יש למלא את השדה'],
    ['typeMismatch', (input) => input.type === 'email'
        ? 'יש להזין כתובת דוא״ל תקינה'
        : 'הערך אינו בפורמט הנכון'],
    ['tooShort', (input) => `יש להזין לפחות ${input.minLength} תווים`],
    ['rangeUnderflow', (input) => `הערך המזערי הוא ${input.min}`],
    ['rangeOverflow', (input) => `הערך המרבי הוא ${input.max}`],
    ['badInput', () => 'יש להזין מספר'],
]

// The message is cleared first, or the field reports that it has one instead
// of why it failed.
function translate(field) {
    if (typeof field.setCustomValidity !== 'function') {
        return
    }

    field.setCustomValidity('')

    const failure = MESSAGES.find(([reason]) => field.validity[reason])

    if (failure) {
        field.setCustomValidity(failure[1](field))
    }
}

function translateForm(form) {
    if (form) {
        Array.from(form.elements).forEach(translate)
    }
}

export function translateFieldErrors() {
    // Listening on document covers every screen. These events don't bubble,
    // so they are caught on the capture phase.
    document.addEventListener('input', (event) => translate(event.target), true)
    document.addEventListener('change', (event) => translate(event.target), true)
    document.addEventListener('invalid', (event) => translate(event.target), true)

    // A field nobody touched has no message yet, so the whole form is done
    // when it is submitted.
    document.addEventListener('click', (event) => {
        const button = event.target.closest?.('button:not([type=button]), [type=submit]')

        translateForm(button?.form)
    }, true)

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            translateForm(event.target.form)
        }
    }, true)
}
