// The installed app opens documents in its own window: a new tab would leave the app (on iOS for Safari),
// without its offline cache or session.
export const documentTarget = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone ? '_self' : '_blank'

const nf = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 1 })

export function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${nf.format(bytes / (1024 * 1024))} MB`
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}
