const SUPPORTED = ['image/jpeg', 'image/png', 'image/webp']

// Cover images use maxEdge 1600, documents 2560.
export async function compressImage(file: File, maxEdge: number, quality = 0.8): Promise<Blob> {
  if (!SUPPORTED.includes(file.type)) return file
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    return file // undecodable: let the server validate it
  }
  const scale = maxEdge / Math.max(bitmap.width, bitmap.height)
  if (scale >= 1) {
    bitmap.close()
    return file
  }
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const ctx = canvas.getContext('2d')!
  // JPEG has no alpha; transparent PNG/WebP pixels would turn black.
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
  return blob ?? file
}
