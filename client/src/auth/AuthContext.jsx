import { createContext, useContext, useEffect, useState } from 'react'
import { api } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        api.get('/api/auth/me')
            .then(setUser)
            .catch(() => setUser(null))
            .finally(() => setLoading(false))
    }, [])

    async function login(username, password) {
        const result = await api.post('/api/auth/login', { username, password })

        setUser({
            employeeId: result.employeeId,
            username: result.username,
            fullName: result.fullName,
            role: result.role,
        })
    }

    async function logout() {
        try {
            await api.post('/api/auth/logout')
        } finally {
            setUser(null)
        }
    }

    const value = {
        user,
        loading,
        login,
        logout,
        isManager: user?.role === 'MANAGER',
    }

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
    const context = useContext(AuthContext)

    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider')
    }

    return context
}
