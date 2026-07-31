import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export default function ProtectedRoute({ children, requireManager = false }) {
    const { user, loading, isManager } = useAuth()

    if (loading) {
        return <p className="notice">Loading…</p>
    }

    if (!user) {
        return <Navigate to="/login" replace />
    }

    if (requireManager && !isManager) {
        return <Navigate to="/" replace />
    }

    return children
}
