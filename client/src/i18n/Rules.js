// As the server responses in English and the website in Hebrew
// using this to translate the responses to Hebrew
export const RULE_TEXT = {
    REST_PERIOD: 'פחות מ-8 שעות מנוחה',
    ONE_SHIFT_PER_DAY: 'כבר משובץ באותו יום',
    MAX_SHIFTS_PER_WEEK: 'הגיע ל-6 משמרות השבוע',
    EMPLOYEE_ON_LEAVE: 'בחופשה',
    EMPLOYEE_CANNOT_WORK: 'סימן שאינו יכול',
    EMPLOYEE_PREFERS_NOT: 'סימן מעדיף שלא',
    MAX_WEEKLY_HOURS: 'חריגה מהיקף המשרה',
    NO_MATCHING_REQUIREMENT: 'המשמרת אינה דורשת תפקיד זה',
}

// The confirmation rules that make changes, would ensure the manager is aware.
// (overtime for example is not included as it doesn't change anything)
export const RULE_CONSEQUENCE = {
    EMPLOYEE_ON_LEAVE: 'יום החופשה יימחק',
    EMPLOYEE_CANNOT_WORK: 'האילוץ שהעובד הגיש יימחק',
    NO_MATCHING_REQUIREMENT: 'השיבוץ יסומן כחריג ולא ייספר באיוש',
}

// Rules that cannot be overridden.
const BLOCKING = new Set([
    'REST_PERIOD',
    'ONE_SHIFT_PER_DAY',
    'MAX_SHIFTS_PER_WEEK',
])

export function isBlocking(rule) {
    return BLOCKING.has(rule)
}

export function ruleText(rule) {
    return RULE_TEXT[rule] ?? rule
}