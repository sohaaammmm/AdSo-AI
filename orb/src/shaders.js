// GLSL for AdSo's energy orb. All shaders are additive or translucent so the canvas can stay transparent.

// Ribbons (wide bands and thin filaments share this). Bends the ribbon over time (morph)
// and sends light along it (flow). uv.x runs along the ribbon, uv.y across it.
export const ribbonVert = /* glsl */ `
uniform float uTime;
uniform float uMorph;
uniform float uSeed;
varying vec2 vUv;
varying vec3 vN;
varying vec3 vView;
varying float vDepth;
void main() {
  vec3 p = position;
  vec3 r = normalize(p);
  float u = uv.x * 6.2831853;
  float d = sin(u * 2.0 + uTime * 0.9 + uSeed) * 0.07
          + sin(u * 3.0 - uTime * 0.6 + uSeed * 1.7) * 0.045
          + sin(u * 5.0 + uTime * 1.3 + uSeed * 0.5) * 0.02;
  p += r * d * uMorph;
  vec3 side = normalize(cross(r, vec3(0.0, 1.0, 0.0)) + vec3(1e-4));
  p += side * sin(u * 2.0 - uTime * 0.7 + uSeed * 2.3) * 0.05 * uMorph;
  vec4 world = modelMatrix * vec4(p, 1.0);
  vec4 mv = viewMatrix * world;
  vView = normalize(-mv.xyz);
  vN = normalize(normalMatrix * normal);
  vUv = uv;
  vDepth = world.z;
  gl_Position = projectionMatrix * mv;
}
`

export const ribbonFrag = /* glsl */ `
uniform float uFlowTime;
uniform float uTime;
uniform float uBright;
uniform float uPulse;
uniform float uSeed;
uniform float uEdge;   // 1 = wide band (bright edges), 0 = thin filament
uniform float uAlpha;
uniform vec3 uColorA;  // electric blue
uniform vec3 uColorB;  // cyan highlight
varying vec2 vUv;
varying vec3 vN;
varying vec3 vView;
varying float vDepth;
void main() {
  float fres = pow(1.0 - abs(dot(normalize(vN), normalize(vView))), 1.4);
  float s = vUv.x;
  // silk: a band is many fine parallel strands that drift slightly, fading softly at both edges
  float soft = sin(vUv.y * 3.14159);
  float strands = pow(0.5 + 0.5 * cos(vUv.y * 6.2831 * 6.0 + sin(s * 12.566 + uTime * 0.5 + uSeed) * 1.5), 6.0);
  float silk = pow(soft, 1.5) * (0.12 + 1.1 * strands);
  float edge = mix(1.0, silk, uEdge);
  // wispy: each ribbon fades in and out along its length
  float wisp = 0.4 + 0.6 * pow(0.5 + 0.5 * sin(s * 6.2831 + uSeed * 3.1 + uTime * 0.22), 1.5);
  // light travelling along the strand
  float t = fract(s * 3.0 - uFlowTime + uSeed);
  float streak = smoothstep(0.0, 0.06, t) * (1.0 - smoothstep(0.06, 0.32, t));
  // generating: fast pulses
  float pulse = uPulse * pow(0.5 + 0.5 * sin(uTime * 7.0 + s * 18.0 + uSeed * 5.0), 8.0);
  // ribbons behind the core are dimmer, for depth
  float depth = 0.22 + 0.78 * smoothstep(-0.9, 0.9, vDepth);
  float I = (edge * 0.75 + fres * 0.25) * wisp * depth;
  I += streak * (0.7 + uPulse * 0.8) * wisp * depth;
  I += pulse * 0.7 * depth;
  vec3 col = mix(uColorA, uColorB, clamp(streak + pulse * 0.6 + fres * 0.35, 0.0, 1.0));
  float a = clamp(I * uAlpha * uBright, 0.0, 1.0);
  gl_FragColor = vec4(col * (1.0 + I * 0.9 * uBright), a);
}
`

// Dark glass core: black centre, midnight blue, blue rim and a cyan reflection.
export const coreVert = /* glsl */ `
varying vec3 vN;
varying vec3 vView;
varying vec3 vPos;
void main() {
  vec4 mv = modelViewMatrix * vec4(position, 1.0);
  vView = normalize(-mv.xyz);
  vN = normalize(normalMatrix * normal);
  vPos = position;
  gl_Position = projectionMatrix * mv;
}
`

export const coreFrag = /* glsl */ `
uniform float uTime;
uniform float uGlow;
varying vec3 vN;
varying vec3 vView;
varying vec3 vPos;
void main() {
  vec3 N = normalize(vN);
  vec3 V = normalize(vView);
  float f = 1.0 - abs(dot(N, V));
  vec3 deep = vec3(0.0, 0.004, 0.018);
  vec3 mid = vec3(0.006, 0.025, 0.12);
  // near-black, a touch of midnight blue in the middle, darkest at its edge so it melts into the glass
  // (linear values: the final output step brightens dark colours, so these stay tiny)
  vec3 col = mix(vec3(0.0006, 0.0025, 0.014), vec3(0.0, 0.0004, 0.002), smoothstep(0.2, 0.9, f));
  col += vec3(0.0004, 0.0015, 0.006) * (0.5 + 0.5 * sin(vPos.x * 3.0 + uTime * 0.4) * sin(vPos.y * 2.6 - uTime * 0.3));
  gl_FragColor = vec4(col, 1.0);
}
`

// Glass shell: only the bright rim (a crescent along the bottom) and a faint reflection. Additive.
export const shellFrag = /* glsl */ `
uniform float uGlow;
varying vec3 vN;
varying vec3 vView;
varying vec3 vPos;
void main() {
  vec3 N = normalize(vN);
  vec3 V = normalize(vView);
  float f = 1.0 - abs(dot(N, V));
  float lower = 0.3 + 0.7 * smoothstep(0.5, -0.8, N.y);
  vec3 rim = vec3(0.12, 0.3, 1.0);
  vec3 col = rim * pow(f, 2.2) * min(1.4 * uGlow, 1.8) * lower;
  vec3 L = normalize(vec3(-0.45, 0.65, 0.6));
  float spec = pow(max(dot(reflect(-L, N), V), 0.0), 120.0);
  col += vec3(0.55, 0.75, 1.0) * spec * 0.18;
  gl_FragColor = vec4(col, clamp(max(col.b, max(col.r, col.g)), 0.0, 1.0));
}
`

// Soft blue atmosphere around the sphere (billboard plane).
export const haloVert = /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`

export const haloFrag = /* glsl */ `
uniform float uGlow;
uniform float uSize;
varying vec2 vUv;
void main() {
  vec2 c = vUv - 0.5;
  float d = length(c) * uSize;
  float lower = 0.45 + 0.55 * smoothstep(0.2, -0.45, c.y / max(length(c), 1e-3) * 0.5);
  float ring = exp(-pow((d - 1.02) / 0.15, 2.0)) * lower;
  float haze = exp(-pow(max(d - 0.98, 0.0) / 0.42, 2.0)) * step(0.98, d);
  float g = ring * 0.36 + haze * 0.3;
  g *= smoothstep(uSize * 0.5, uSize * 0.42, d);
  vec3 col = mix(vec3(0.1, 0.25, 1.0), vec3(0.5, 0.65, 1.0), ring * 0.5);
  gl_FragColor = vec4(col, clamp(g * uGlow, 0.0, 1.0));
}
`

// Floating particles.
export const sparkVert = /* glsl */ `
attribute float aSeed;
uniform float uTime;
uniform float uPx;
varying float vTw;
void main() {
  vec3 p = position + normalize(position) * sin(uTime * 0.6 + aSeed * 6.2831) * 0.06;
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_PointSize = (2.0 + 3.5 * fract(aSeed * 7.31)) * uPx * (4.0 / -mv.z);
  vTw = 0.35 + 0.65 * (0.5 + 0.5 * sin(uTime * 2.2 + aSeed * 40.0));
  gl_Position = projectionMatrix * mv;
}
`

export const sparkFrag = /* glsl */ `
uniform float uSparks;
varying float vTw;
void main() {
  float d = length(gl_PointCoord - 0.5);
  float a = smoothstep(0.5, 0.0, d) * uSparks * vTw;
  gl_FragColor = vec4(vec3(0.45, 0.75, 1.0) * 1.4, a);
}
`
