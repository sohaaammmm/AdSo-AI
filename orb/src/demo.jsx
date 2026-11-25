// Dev preview (npm run dev): try each state, intensity and size, on dark or checker background.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import EnergyOrb from './EnergyOrb.jsx'

function Demo() {
  const [state, setState] = useState('idle')
  const [intensity, setIntensity] = useState(1)
  const [size, setSize] = useState(360)
  const [checker, setChecker] = useState(false)
  const bg = checker
    ? 'repeating-conic-gradient(#1a1d26 0% 25%, #2a2f3c 0% 50%) 50% / 24px 24px'
    : 'radial-gradient(110% 60% at 50% 0%, rgba(47,91,234,.35), rgba(47,91,234,0) 70%), #07090F'
  return (
    <div style={{ minHeight: '100vh', background: bg, color: '#F3F5FA', fontFamily: 'Inter, system-ui, sans-serif', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: 24, boxSizing: 'border-box' }}>
      <EnergyOrb state={state} intensity={intensity} size={size} />
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
        {['idle', 'thinking', 'generating', 'resting'].map((s) => (
          <button key={s} id={`state-${s}`} onClick={() => setState(s)} style={{ minHeight: 44, padding: '0 18px', borderRadius: 999, border: '1px solid rgba(255,255,255,.15)', background: state === s ? '#3D6BFF' : 'rgba(255,255,255,.06)', color: '#fff', cursor: 'pointer' }}>{s}</button>
        ))}
      </div>
      <label>Intensity {intensity.toFixed(1)} <input type="range" min="0.4" max="2" step="0.1" value={intensity} onChange={(e) => setIntensity(Number(e.target.value))} /></label>
      <label>Size {size}px <input type="range" min="96" max="520" step="8" value={size} onChange={(e) => setSize(Number(e.target.value))} /></label>
      <label><input type="checkbox" checked={checker} onChange={(e) => setChecker(e.target.checked)} /> Checker background (shows transparency)</label>
    </div>
  )
}

createRoot(document.getElementById('root')).render(<Demo />)
