/**
 * Photos shrunk on the phone before they are sent (spec section 7 "photos compressed", ADR-038): at most 1,600 pixels
 * on the long side, JPEG quality 0.8. The server re-encodes them anyway; this only saves the person's data on a slow
 * network. Browsers apply the EXIF orientation when they draw the image, so the photo is the right way up.
 */

/** Long side of a photo sent to the server, in pixels. */
export const MAX_PHOTO_SIDE = 1600

/** The size of a photo after shrinking: never enlarged, ratio kept, at least one pixel. */
export function shrunkSize(width: number, height: number, maxSide = MAX_PHOTO_SIDE): { width: number; height: number } {
  const scale = Math.min(1, maxSide / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

/**
 * The photo shrunk as a JPEG file named after the original. Anything that is not an image, or that the browser cannot
 * decode, is returned unchanged: the server decides (415 for a wrong content).
 */
export async function shrinkPhoto(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || typeof createImageBitmap !== 'function') return file
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    return file
  }
  const size = shrunkSize(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = size.width
  canvas.height = size.height
  const context = canvas.getContext('2d')
  if (!context) return file
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, size.width, size.height)
  context.drawImage(bitmap, 0, 0, size.width, size.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.8))
  if (!blob) return file
  return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' })
}
