import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { translateFieldErrors } from './i18n/validation'
import './index.css'

translateFieldErrors()

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <App />
    </StrictMode>
)
