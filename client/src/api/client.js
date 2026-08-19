export class ApiError extends Error {
    constructor(status, message, code, body) {
        super(message)
        this.status = status

        // Set only where the server needs the screen to tell two failures apart.
        this.code = code

        // Carries the whole response, which only the assignment rejection reads.
        this.body = body

    }
}

async function request(method, path, body) {
    const headers = {}

    if (body !== undefined) {
        headers['Content-Type'] = 'application/json'
    }

    const response = await fetch(path, {
        method,
        headers,
        // Sends the HttpOnly cookie with every request, since the token can't be
        // read from here.
        credentials: 'include',
        body: body === undefined ? undefined : JSON.stringify(body),
    })

    if (response.status === 204) {
        return null
    }

    const text = await response.text()
    const payload = text ? JSON.parse(text) : null

    if (!response.ok) {

        // token expired or cleared, returns to the login screen
        // Skips the auth paths so a failed login can show its own message.
        if (response.status === 401 && !path.includes('/api/auth/')) {
            // Loads the whole page again so nothing is left from the session
            // that ended.
            window.location.href = '/login'
            return null
        }
        throw new ApiError(response.status, payload?.message ?? 'Request failed',
            payload?.code, payload)

    }

    return payload
}

export const api = {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    delete: (path) => request('DELETE', path),
}