import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function LoginPage() {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    const { login } = useAuth()
    const navigate = useNavigate()

    async function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            await login(username, password)
            navigate('/')
        } catch (err) {
            setError(err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="login-screen">
            <form className="login-card" onSubmit={handleSubmit}>
                <h1>Shift Scheduler</h1>
                <p className="subtitle">Sign in to continue</p>

                <label htmlFor="username">Username</label>
                <input
                    id="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    required
                />

                <label htmlFor="password">Password</label>
                <input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    required
                />

                {error && <p className="error">{error}</p>}

                <button type="submit" disabled={submitting}>
                    {submitting ? 'Signing in…' : 'Sign in'}
                </button>
            </form>
        </div>
    )
}
