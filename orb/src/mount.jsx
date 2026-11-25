// Entry for web mock-ups: turns every element with class "adso-orb3d" into a live EnergyOrb.
// Reads data-state ("idle" | "thinking" | "generating" | "resting") and data-intensity,
// follows changes to them, and frees the WebGL context when the element leaves the page.
import { createRoot } from 'react-dom/client'
import EnergyOrb from './EnergyOrb.jsx'

const roots = new Map()

function render(el) {
  const root = roots.get(el)
  if (!root) return
  root.render(<EnergyOrb state={el.dataset.state || 'idle'} intensity={Number(el.dataset.intensity) || 1} size="100%" />)
}

function mount(el) {
  if (roots.has(el)) return
  roots.set(el, createRoot(el))
  render(el)
}

function scan(node) {
  if (!(node instanceof Element)) return
  if (node.classList.contains('adso-orb3d')) mount(node)
  node.querySelectorAll('.adso-orb3d').forEach(mount)
}

function sweep() {
  for (const [el, root] of roots) {
    if (!el.isConnected) {
      root.unmount()
      roots.delete(el)
    }
  }
}

function start() {
  scan(document.body)
  new MutationObserver((records) => {
    let removed = false
    for (const r of records) {
      if (r.type === 'attributes') render(r.target)
      r.addedNodes.forEach(scan)
      if (r.removedNodes.length) removed = true
    }
    if (removed) sweep()
  }).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-state', 'data-intensity'] })
}

if (document.body) start()
else document.addEventListener('DOMContentLoaded', start)
