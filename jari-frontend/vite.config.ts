import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy /api and /auth to the gateway so the browser makes same-origin
    // requests. This is not a convenience - it is what makes the browser work
    // at all. A cross-origin call would be preflighted, and a CORS preflight
    // carries no Authorization header, so the gateway's AuthenticationFilter
    // rejects it with 401 before the real request is ever sent. Same-origin
    // requests are not preflighted, so the problem does not arise.
    //
    // Dev server only. Serving this app from any other origin needs real CORS
    // on the gateway (including short-circuiting OPTIONS ahead of auth), or
    // serving the built assets through the gateway itself. Neither exists yet.
    proxy: {
      '/api': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
    },
  },
})
