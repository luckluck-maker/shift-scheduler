import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

// The frame every signed-in screen sits inside: the top bar and the menu.
// Outlet is where the routed page gets rendered.
export default function Layout() {
    const { user, logout, isManager } = useAuth()

    const links = isManager
        ? [
            { to: '/my-schedule', label: 'הסידור' },
            { to: '/schedule', label: 'בניית סידור' },
            { to: '/employees', label: 'עובדים' },
            { to: '/constraints', label: 'אילוצים' },
            { to: '/leaves', label: 'היעדרויות' },
            { to: '/positions-and-shift-types', label: 'תפקידים ומשמרות' },
        ]
        : [
            { to: '/my-schedule', label: 'הסידור' },
            { to: '/constraints', label: 'האילוצים שלי' },
        ]

    return (
        <div className="app">
            <header className="topbar">
                <span className="brand">מסדרים ת'סידור</span>

                <nav className="menu">
                    {links.map((link) => (
                        <NavLink key={link.to} to={link.to} className="menu-link">
                            {link.label}
                        </NavLink>
                    ))}
                </nav>

                <div className="topbar-end">
                    <span className="who">{user.fullName ?? user.username}</span>
                    <button className="link-button" onClick={logout}>יציאה</button>
                </div>
            </header>

            <main className="content">
                <Outlet />
            </main>
        </div>
    )
}
