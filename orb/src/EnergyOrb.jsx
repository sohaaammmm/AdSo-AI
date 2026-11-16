import { useEffect, useMemo, useRef, useState } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { AdaptiveDpr, Billboard, Float } from '@react-three/drei'
import { Bloom, EffectComposer } from '@react-three/postprocessing'
import * as THREE from 'three'
import {
  coreFrag, coreVert, haloFrag, haloVert, ribbonFrag, ribbonVert, shellFrag, sparkFrag, sparkVert,
} from './shaders.js'

/** Target look for each state. Values are eased toward, so changes are always smooth. */
const STATES = {
  idle: { speed: 1.0, bright: 1.0, glow: 1.0, sparks: 0.22, flow: 0.12, pulse: 0.0, morph: 1.0, bloom: 0.75 },
  thinking: { speed: 2.3, bright: 1.3, glow: 1.4, sparks: 0.95, flow: 0.3, pulse: 0.12, morph: 1.5, bloom: 1.15 },
  generating: { speed: 1.6, bright: 1.45, glow: 1.5, sparks: 0.6, flow: 1.15, pulse: 1.0, morph: 1.25, bloom: 1.35 },
  resting: { speed: 0.4, bright: 0.6, glow: 0.65, sparks: 0.06, flow: 0.06, pulse: 0.0, morph: 0.6, bloom: 0.5 },
}
const RESTING_MS = 2600
const R = 1.0 // orb radius in world units
const BLUE = new THREE.Color('#1f4dff')
const CYAN = new THREE.Color('#a9c6ff')

function rng(seed) {
  let s = seed
  return () => {
    s = (s + 0x6d2b79f5) | 0
    let t = Math.imul(s ^ (s >>> 15), 1 | s)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * A closed loop around the sphere that mostly stays in the lower half and sweeps up the sides,
 * like silk flowing inside a glass ball.
 */
function makeCurve(r) {
  const base = -0.38 + r() * 0.22 // latitude in radians, below the equator
  const amp = 0.22 + r() * 0.45
  const k = r() < 0.65 ? 1 : 2
  const ph = r() * Math.PI * 2
  const ph2 = r() * Math.PI * 2
  const pts = []
  const N = 96
  for (let j = 0; j < N; j++) {
    const a = (j / N) * Math.PI * 2
    const lat = base + amp * Math.sin(k * a + ph) + 0.08 * Math.sin(3 * a + ph2)
    const rad = R * (0.9 + 0.08 * (0.5 + 0.5 * Math.sin(2 * a + ph2)))
    pts.push(new THREE.Vector3(Math.cos(lat) * Math.cos(a), Math.sin(lat), Math.cos(lat) * Math.sin(a)).multiplyScalar(rad))
  }
  return new THREE.CatmullRomCurve3(pts, true, 'catmullrom', 0.5)
}

/** A flat, twisting ribbon that lies along the sphere and changes width along its length. */
function makeBand(curve, width, twist, r) {
  const S = 360
  const pos = new Float32Array((S + 1) * 6)
  const uv = new Float32Array((S + 1) * 4)
  const index = []
  const k = 1 + Math.floor(r() * 3)
  const m = 1 + Math.floor(r() * 2)
  const ph = r() * Math.PI * 2
  const p = new THREE.Vector3()
  const t = new THREE.Vector3()
  const rad = new THREE.Vector3()
  const side = new THREE.Vector3()
  const w = new THREE.Vector3()
  for (let i = 0; i <= S; i++) {
    const u = i / S
    const uu = u % 1
    curve.getPointAt(uu, p)
    curve.getTangentAt(uu, t)
    rad.copy(p).normalize()
    side.crossVectors(t, rad).normalize()
    const ang = twist * Math.sin(u * Math.PI * 2 * k + ph)
    w.copy(side).multiplyScalar(Math.cos(ang)).addScaledVector(rad, Math.sin(ang))
    const half = (width * (0.55 + 0.45 * Math.pow(Math.sin(u * Math.PI * m + ph), 2))) / 2
    pos.set([p.x - w.x * half, p.y - w.y * half, p.z - w.z * half, p.x + w.x * half, p.y + w.y * half, p.z + w.z * half], i * 6)
    uv.set([u, 0, u, 1], i * 4)
    if (i < S) {
      const a = i * 2
      index.push(a, a + 1, a + 2, a + 1, a + 3, a + 2)
    }
  }
  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2))
  g.setIndex(index)
  g.computeVertexNormals()
  return g
}

function buildRibbons() {
  const r = rng(20261114)
  const list = []
  const add = (geometry, edge, alpha) => list.push({
    geometry, edge, alpha,
    seed: r() * 10,
    speed: (0.1 + r() * 0.28) * (r() < 0.5 ? -1 : 1), // each strand its own speed and direction
    rx: (r() - 0.5) * 0.3, ry: r() * Math.PI * 2, rz: (r() - 0.5) * 0.3,
    wx: 0.05 + r() * 0.12, wz: 0.04 + r() * 0.1,
  })
  // silk sheets: wide bands drawn as many fine parallel strands
  for (let i = 0; i < 8; i++) add(makeBand(makeCurve(r), 0.18 + r() * 0.16, 0.4 + r() * 0.8, r), 1, 1.0)
  // a few loose single strands
  for (let i = 0; i < 3; i++) add(new THREE.TubeGeometry(makeCurve(r), 320, 0.003 + r() * 0.003, 6, true), 0, 0.45)
  return list
}

function buildSparks(count) {
  const r = rng(77)
  const pos = new Float32Array(count * 3)
  const seed = new Float32Array(count)
  const v = new THREE.Vector3()
  for (let i = 0; i < count; i++) {
    v.set(r() * 2 - 1, r() * 2 - 1, r() * 2 - 1).normalize().multiplyScalar(1.22 + r() * 0.45)
    pos.set([v.x, v.y, v.z], i * 3)
    seed[i] = r()
  }
  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  g.setAttribute('aSeed', new THREE.BufferAttribute(seed, 1))
  return g
}

function OrbScene({ state, intensity, reduceMotion }) {
  const dpr = useThree((s) => s.viewport.dpr)
  const shared = useMemo(() => ({
    uTime: { value: 0 }, uFlowTime: { value: 0 }, uBright: { value: 1 }, uGlow: { value: 1 },
    uPulse: { value: 0 }, uMorph: { value: 1 }, uSparks: { value: 0.22 }, uPx: { value: 1 },
  }), [])
  const ribbons = useMemo(buildRibbons, [])
  const materials = useMemo(() => ribbons.map((rb) => new THREE.ShaderMaterial({
    vertexShader: ribbonVert, fragmentShader: ribbonFrag,
    uniforms: {
      uTime: shared.uTime, uFlowTime: shared.uFlowTime, uBright: shared.uBright, uPulse: shared.uPulse, uMorph: shared.uMorph,
      uSeed: { value: rb.seed }, uEdge: { value: rb.edge }, uAlpha: { value: rb.alpha },
      uColorA: { value: BLUE }, uColorB: { value: CYAN },
    },
    transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, toneMapped: false,
  })), [ribbons, shared])
  const sparkGeo = useMemo(() => buildSparks(220), [])
  const cur = useRef({ ...STATES.idle })
  const clock = useRef({ time: 0, flow: 0 })
  const groups = useRef([])
  const whole = useRef()
  const sparks = useRef()
  const bloom = useRef()

  useEffect(() => () => { materials.forEach((m) => m.dispose()); ribbons.forEach((rb) => rb.geometry.dispose()); sparkGeo.dispose() }, [materials, ribbons, sparkGeo])

  useFrame((_, rawDt) => {
    const dt = Math.min(rawDt, 0.05)
    const target = STATES[state] || STATES.idle
    const c = cur.current
    const ease = 1 - Math.exp(-dt * (state === 'resting' ? 1.4 : 2.4))
    for (const key in target) c[key] += (target[key] - c[key]) * ease
    const motion = reduceMotion ? 0.35 : 1
    clock.current.time += dt * c.speed * motion
    clock.current.flow += dt * c.flow * motion
    const T = clock.current.time
    shared.uTime.value = T
    shared.uFlowTime.value = clock.current.flow
    shared.uBright.value = c.bright * intensity
    shared.uGlow.value = c.glow * intensity
    shared.uPulse.value = c.pulse
    shared.uMorph.value = c.morph
    shared.uSparks.value = c.sparks
    shared.uPx.value = dpr
    ribbons.forEach((rb, i) => {
      const g = groups.current[i]
      if (g) g.rotation.set(rb.rx + Math.sin(T * rb.wx + rb.seed) * 0.14, rb.ry + T * rb.speed, rb.rz + Math.cos(T * rb.wz + rb.seed * 1.3) * 0.12)
    })
    if (whole.current) {
      whole.current.rotation.y = T * 0.06
      whole.current.rotation.x = Math.sin(T * 0.045) * 0.08
    }
    if (sparks.current) sparks.current.rotation.y = -T * 0.04
    if (bloom.current) bloom.current.intensity = c.bloom * intensity
  })

  return (
    <>
      <Float speed={1.1} rotationIntensity={0.15} floatIntensity={0.35} floatingRange={[-0.05, 0.05]}>
        <Billboard>
          <mesh renderOrder={-2}>
            <planeGeometry args={[3.2, 3.2]} />
            <shaderMaterial
              vertexShader={haloVert} fragmentShader={haloFrag}
              uniforms={{ uGlow: shared.uGlow, uSize: { value: 3.2 } }}
              transparent depthWrite={false} blending={THREE.AdditiveBlending} toneMapped={false}
            />
          </mesh>
        </Billboard>
        {/* dark core: solid, so strands behind it are hidden */}
        <mesh renderOrder={-1}>
          <sphereGeometry args={[R * 0.78, 64, 48]} />
          <shaderMaterial vertexShader={coreVert} fragmentShader={coreFrag} uniforms={{ uTime: shared.uTime }} />
        </mesh>
        {/* glass shell: bright rim only */}
        <mesh renderOrder={2}>
          <sphereGeometry args={[R * 1.0, 64, 48]} />
          <shaderMaterial
            vertexShader={coreVert} fragmentShader={shellFrag}
            uniforms={{ uGlow: shared.uGlow }}
            transparent depthWrite={false} blending={THREE.AdditiveBlending} toneMapped={false}
          />
        </mesh>
        <group ref={whole}>
          {ribbons.map((rb, i) => (
            <group key={i} ref={(el) => { groups.current[i] = el }}>
              <mesh geometry={rb.geometry} material={materials[i]} />
            </group>
          ))}
        </group>
        <points ref={sparks} geometry={sparkGeo}>
          <shaderMaterial
            vertexShader={sparkVert} fragmentShader={sparkFrag}
            uniforms={{ uTime: shared.uTime, uSparks: shared.uSparks, uPx: shared.uPx }}
            transparent depthWrite={false} blending={THREE.AdditiveBlending} toneMapped={false}
          />
        </points>
      </Float>
      <EffectComposer multisampling={0}>
        <Bloom ref={bloom} mipmapBlur luminanceThreshold={0.32} luminanceSmoothing={0.3} intensity={0.8} radius={0.65} />
      </EffectComposer>
    </>
  )
}

/**
 * AdSo's AI energy orb.
 * @param {'idle'|'thinking'|'generating'|'resting'} state  resting fades down, then returns to idle by itself
 * @param {number|string} size  px number or any CSS length ('100%')
 * @param {number} intensity  glow multiplier, 1 = default
 */
export default function EnergyOrb({ state = 'idle', size = 320, intensity = 1, className, style }) {
  const [shown, setShown] = useState(state)
  useEffect(() => {
    setShown(state)
    if (state !== 'resting') return undefined
    const id = setTimeout(() => setShown('idle'), RESTING_MS)
    return () => clearTimeout(id)
  }, [state])
  const reduceMotion = useMemo(() => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches, [])
  const dim = typeof size === 'number' ? `${size}px` : size
  return (
    <div className={className} style={{ width: dim, height: dim, ...style }} role="img" aria-label={`AdSo AI, ${shown}`}>
      <Canvas
        dpr={[1, 1.75]}
        gl={{ alpha: true, antialias: false, powerPreference: 'high-performance', premultipliedAlpha: false }}
        camera={{ position: [0, 0, 5], fov: 36 }}
        onCreated={({ gl }) => gl.setClearColor(0x000000, 0)}
        style={{ background: 'transparent' }}
      >
        <AdaptiveDpr pixelated={false} />
        <OrbScene state={shown} intensity={intensity} reduceMotion={reduceMotion} />
      </Canvas>
    </div>
  )
}
