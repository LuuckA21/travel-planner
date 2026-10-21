import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { m } from '../i18n/it'
import DocumentUploadButton from './DocumentUploadButton'

vi.mock('../lib/images', () => ({
  compressImage: vi.fn(async (file: File) => new Blob(['small'], { type: file.type === 'image/png' ? 'image/jpeg' : file.type })),
}))

const t = m.documents
const fetchMock = vi.fn()
const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code, detail: 'x' }), { status, headers: { 'Content-Type': 'application/problem+json' } })

function renderButton(bookingId?: string) {
  const { container } = render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
        <DocumentUploadButton tripId="t1" bookingId={bookingId} />
      </QueryClientProvider>
    </MantineProvider>,
  )
  return container.querySelector('input[type=file]') as HTMLInputElement
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

test('accepts only PDF and image types', () => {
  expect(renderButton().accept).toBe('application/pdf,image/jpeg,image/png,image/webp')
})

test('shows Italian message on 415', async () => {
  fetchMock.mockResolvedValue(problem(415, 'UNSUPPORTED_FILE_TYPE'))
  const input = renderButton()
  await userEvent.setup({ applyAccept: false }).upload(input, new File(['x'], 'foto.heic', { type: 'image/heic' }))
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent(t.unsupportedType)
  expect(t.unsupportedType).toMatch(/PDF.*JPEG.*PNG.*WebP/)
})

test('shows Italian message on 413', async () => {
  fetchMock.mockResolvedValue(problem(413, 'FILE_TOO_LARGE'))
  const input = renderButton()
  await userEvent.setup().upload(input, new File(['x'], 'big.pdf', { type: 'application/pdf' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(t.tooLarge)
})

test('shows a generic Italian message on other failures', async () => {
  fetchMock.mockResolvedValue(problem(500, 'X'))
  await userEvent.setup().upload(renderButton(), new File(['x'], 'a.pdf', { type: 'application/pdf' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.genericError)
})

test('sends bookingId when given', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 'd1' }), { status: 201 }))
  await userEvent.setup().upload(renderButton('b1'), new File(['x'], 'a.pdf', { type: 'application/pdf' }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/trips/t1/documents')
  expect(init.method).toBe('POST')
  const form = init.body as FormData
  expect(form.get('bookingId')).toBe('b1')
  expect((form.get('file') as File).name).toBe('a.pdf')
})

test('omits bookingId when not given', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 'd1' }), { status: 201 }))
  await userEvent.setup().upload(renderButton(), new File(['x'], 'a.pdf', { type: 'application/pdf' }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect((fetchMock.mock.calls[0][1].body as FormData).has('bookingId')).toBe(false)
})

test('compresses images but keeps the original filename', async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 'd1' }), { status: 201 }))
  await userEvent.setup().upload(renderButton(), new File(['x'], 'scontrino.png', { type: 'image/png' }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  const file = (fetchMock.mock.calls[0][1].body as FormData).get('file') as File
  expect(file.name).toBe('scontrino.png')
  expect(file.size).toBe(5)
})

test('the same file can be picked again after a failure', async () => {
  fetchMock.mockResolvedValueOnce(problem(500, 'X')).mockResolvedValueOnce(new Response(JSON.stringify({ id: 'd1' }), { status: 201 }))
  const input = renderButton()
  const user = userEvent.setup()
  const file = new File(['x'], 'a.pdf', { type: 'application/pdf' })
  await user.upload(input, file)
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.genericError)
  await user.upload(input, file)
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
})
