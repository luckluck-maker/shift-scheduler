import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Sends anyone without a session to the login page, and anyone who isn't a
// manager back to the home page.
export default function ProtectedRoute({ children, requireManager = false }) {
    const { user, loading, isManager } = useAuth()

    // Waits for the answer from /api/auth/me, so the screen doesn't jump to the
    // login page on every refresh.
    if (loading) {
        return <p className="notice">טוען..</p>
    }

    if (!user) {
        return <Navigate to="/login" replace />
    }

    if (requireManager && !isManager) {
        return <Navigate to="/" replace />
    }

    return children
}
