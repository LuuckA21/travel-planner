const SUPPORTED = ['image/jpeg', 'image/png', 'image/webp']

// Cover images use maxEdge 1600, documents 2560.
export async function compressImage(file: File, maxEdge: number, quality = 0.8): Promise<Blob> {
  if (!SUPPORTED.includes(file.type)) return file
  const bitmap = await createImageBitmap(file)
  const scale = maxEdge / Math.max(bitmap.width, bitmap.height)
  if (scale >= 1) {
    bitmap.close()
    return file
  }
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
  return blob ?? file
}
