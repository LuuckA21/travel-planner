import { afterEach, expect, test, vi } from 'vitest'
import { compressImage } from './images'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function stubCanvas(width: number, height: number) {
  const drawImage = vi.fn()
  const toBlob = vi.fn((cb: BlobCallback, type: string) => cb(new Blob(['x'], { type })))
  const canvas = { width: 0, height: 0, getContext: () => ({ drawImage }), toBlob }
  vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width, height, close() {} }))
  vi.spyOn(document, 'createElement').mockReturnValue(canvas as never)
  return { canvas, toBlob }
}

test('downscales a large image to maxEdge as JPEG', async () => {
  const { canvas, toBlob } = stubCanvas(4000, 2000)
  const out = await compressImage(new File(['x'], 'a.png', { type: 'image/png' }), 1600)
  expect([canvas.width, canvas.height]).toEqual([1600, 800])
  expect(toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.8)
  expect(out.type).toBe('image/jpeg')
})

test('returns the original file when already small or not a supported image', async () => {
  stubCanvas(800, 600)
  const small = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
  expect(await compressImage(small, 1600)).toBe(small)
  const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' })
  expect(await compressImage(pdf, 1600)).toBe(pdf)
})
