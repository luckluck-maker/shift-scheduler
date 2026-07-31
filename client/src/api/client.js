export class ApiError extends Error {
    constructor(status, message) {
        super(message)
        this.status = status
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
        credentials: 'include',
        body: body === undefined ? undefined : JSON.stringify(body),
    })

    if (response.status === 204) {
        return null
    }

    const text = await response.text()
    const payload = text ? JSON.parse(text) : null

    if (!response.ok) {
        throw new ApiError(response.status, payload?.message ?? 'Request failed')
    }

    return payload
}

export const api = {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    delete: (path) => request('DELETE', path),
}