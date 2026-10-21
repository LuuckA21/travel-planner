import react from '@vitejs/plugin-react'
import { configDefaults, defineConfig } from 'vitest/config'
import { VitePWA } from 'vite-plugin-pwa'

const proxy = { '/api': 'http://localhost:8080' }

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      manifest: {
        name: 'Travel Planner',
        short_name: 'Viaggi',
        lang: 'it',
        display: 'standalone',
        start_url: '/',
        theme_color: '#228be6',
        background_color: '#ffffff',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        // The first service worker controls the open page at once, so documents opened right away get cached.
        clientsClaim: true,
        // Only document files and covers; `?download=true` (attachment) does not match `file$`. No other /api route.
        runtimeCaching: [/\/api\/documents\/[^/]+\/file$/, /\/api\/trips\/[^/]+\/cover/].map((urlPattern) => ({
          urlPattern,
          handler: 'CacheFirst' as const,
          options: { cacheName: 'documents', cacheableResponse: { statuses: [200] } },
        })),
      },
    }),
  ],
  server: { proxy },
  preview: { proxy },
  test: {
    environment: 'jsdom',
    // Playwright specs run with `npm run e2e`.
    exclude: [...configDefaults.exclude, 'e2e/**'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
