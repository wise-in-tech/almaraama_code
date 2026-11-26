import { describe, expect, it } from 'vitest'
import { MAX_PHOTO_SIDE, shrinkPhoto, shrunkSize } from '.'

describe('photos shrunk before sending (ADR-038)', () => {
  it('reduces the long side to 1,600 pixels and keeps the ratio', () => {
    expect(shrunkSize(4000, 3000)).toEqual({ width: MAX_PHOTO_SIDE, height: 1200 })
    expect(shrunkSize(3000, 4000)).toEqual({ width: 1200, height: MAX_PHOTO_SIDE })
  })

  it('never enlarges a small photo', () => {
    expect(shrunkSize(640, 480)).toEqual({ width: 640, height: 480 })
  })

  it('leaves a file that is not an image unchanged, for the server to refuse', async () => {
    const pdf = new File(['%PDF-1.7'], 'notice.pdf', { type: 'application/pdf' })
    await expect(shrinkPhoto(pdf)).resolves.toBe(pdf)
  })
})
