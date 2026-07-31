import { useAuth } from '../auth/AuthContext'

export default function HomePage() {
    const { user, logout, isManager } = useAuth()

    return (
        <div className="page">
            <header className="page-header">
                <h1>Shift Scheduler</h1>
                <button className="link-button" onClick={logout}>Sign out</button>
            </header>

            <section className="card">
                <h2>Signed in</h2>
                <dl>
                    <dt>Username</dt>
                    <dd>{user.username}</dd>
                    <dt>Employee ID</dt>
                    <dd>{user.employeeId}</dd>
                    <dt>Role</dt>
                    <dd>{user.role}</dd>
                </dl>

                {isManager && <p className="hint">Manager screens will appear here.</p>}
            </section>
        </div>
    )
}
