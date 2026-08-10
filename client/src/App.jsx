import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom'
import {AuthProvider, useAuth} from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import EmployeesPage from './pages/EmployeesPage'
import PositionsAndShiftTypesPage from './pages/PositionsAndShiftTypesPage'
import LeavesPage from './pages/LeavesPage'
import MySchedulePage from './pages/MySchedulePage'
import ConstraintsPage from './pages/ConstraintsPage'
import ScheduleBuilderPage from './pages/ScheduleBuilderPage'

export default function App() {
    return (<BrowserRouter>
        <AuthProvider>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route element={<ProtectedRoute><Layout/></ProtectedRoute>}>
                    <Route index element={<Home/>}/>
                    <Route path="employees"
                           element={<ProtectedRoute requireManager><EmployeesPage/></ProtectedRoute>}/>
                    <Route path="positions-and-shift-types"
                           element={<ProtectedRoute requireManager><PositionsAndShiftTypesPage
                           /></ProtectedRoute>}/>
                    <Route path="leaves" element={<ProtectedRoute requireManager><LeavesPage/></ProtectedRoute>}
                    />
                    <Route path="my-schedule" element={<MySchedulePage/>}
                    />
                    <Route path="constraints" element={<ConstraintsPage/>}
                    />
                    <Route path="schedule"
                           element={<ProtectedRoute requireManager><ScheduleBuilderPage/></ProtectedRoute>}
                    />
                </Route>
            </Routes>
        </AuthProvider>
    </BrowserRouter>)
}

// Default pages for manager and employee
function Home() {
    const { isManager } = useAuth()
    return <Navigate to={isManager ? '/schedule' : '/my-schedule'} replace />
}
