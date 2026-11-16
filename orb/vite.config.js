import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const here = (p) => fileURLToPath(new URL(p, import.meta.url))

// `npm run dev`         -> preview page (index.html + src/demo.jsx)
// `npm run build`       -> one classic script, app/src/main/assets/orb/orb.js, loaded offline by the WebView
// `npm run build:mount` -> dist/orb-mount.js, turns every .adso-orb3d element on a web page into an orb
const mountBuild = process.env.ORB_ENTRY === 'mount'

export default defineConfig(({ command }) => ({
  plugins: [react()],
  server: { host: '127.0.0.1', port: 5174 },
  define: command === 'build' ? { 'process.env.NODE_ENV': '"production"' } : {},
  build: {
    outDir: mountBuild ? here('dist') : here('../app/src/main/assets/orb'),
    emptyOutDir: false,
    copyPublicDir: false,
    lib: mountBuild
      ? { entry: here('src/mount.jsx'), name: 'AdsoOrbMount', formats: ['iife'], fileName: () => 'orb-mount.js' }
      : { entry: here('src/embed.jsx'), name: 'AdsoOrbBundle', formats: ['iife'], fileName: () => 'orb.js' },
    rollupOptions: { output: { inlineDynamicImports: true } },
    target: 'es2019',
  },
}))
