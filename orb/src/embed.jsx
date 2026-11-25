// Entry for the Android app: a full-window transparent orb controlled from Kotlin with
//   window.AdsoOrb.setState('idle' | 'thinking' | 'generating' | 'resting')
//   window.AdsoOrb.setIntensity(1.0)
import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import EnergyOrb from './EnergyOrb.jsx'

const VALID = ['idle', 'thinking', 'generating', 'resting']
const params = new URLSearchParams(location.hash.slice(1))

function App() {
  const [state, setState] = useState(VALID.includes(params.get('state')) ? params.get('state') : 'idle')
  const [intensity, setIntensity] = useState(Number(params.get('intensity')) || 1)
  useEffect(() => {
    window.AdsoOrb = {
      setState: (s) => { if (VALID.includes(s)) setState(s) },
      setIntensity: (v) => { const n = Number(v); if (n > 0 && n < 5) setIntensity(n) },
    }
    return () => { delete window.AdsoOrb }
  }, [])
  return <EnergyOrb state={state} intensity={intensity} size="100%" />
}

createRoot(document.getElementById('root')).render(<App />)
