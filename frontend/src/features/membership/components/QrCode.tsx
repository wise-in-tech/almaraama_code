import qrcode from 'qrcode-generator'
import { useMemo } from 'react'
import { downloadBlob } from '@/shared/download'

/** The dark modules of a QR code for this text, as a square matrix (error correction M, size chosen automatically). */
export function qrMatrix(text: string): boolean[][] {
  const code = qrcode(0, 'M')
  code.addData(text)
  code.make()
  const size = code.getModuleCount()
  return Array.from({ length: size }, (_, row) => Array.from({ length: size }, (_, col) => code.isDark(row, col)))
}

/** The SVG text of a QR code with a 4-module quiet zone, built from the matrix (no HTML injection, ADR-034). */
export function qrSvg(matrix: boolean[][]): string {
  const size = matrix.length + 8
  const rects = matrix
    .flatMap((row, y) => row.map((dark, x) => (dark ? `<rect x="${x + 4}" y="${y + 4}" width="1" height="1"/>` : '')))
    .join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">`
    + `<rect width="${size}" height="${size}" fill="#fff"/><g fill="#000">${rects}</g></svg>`
}

/**
 * A QR code drawn by React as SVG elements (D2, criterion 4): the browser produces the image, the server none
 * (ADR-034). {@code label} is read by screen readers.
 */
export function QrCode({ text, label, className }: { text: string; label: string; className?: string }) {
  const matrix = useMemo(() => qrMatrix(text), [text])
  const size = matrix.length + 8
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${size} ${size}`}
      shapeRendering="crispEdges"
      className={className}
    >
      <rect width={size} height={size} fill="#fff" />
      <g fill="#000">
        {matrix.flatMap((row, y) =>
          row.map((dark, x) => (dark ? <rect key={`${x}-${y}`} x={x + 4} y={y + 4} width={1} height={1} /> : null)),
        )}
      </g>
    </svg>
  )
}

/** Downloads the QR code as an SVG file, sharp at any print size. */
export function downloadQrSvg(text: string, fileName: string): void {
  downloadBlob(new Blob([qrSvg(qrMatrix(text))], { type: 'image/svg+xml' }), fileName)
}

/** Downloads the QR code as a PNG of about 1,000 pixels, drawn on a canvas. */
export function downloadQrPng(text: string, fileName: string): void {
  const matrix = qrMatrix(text)
  const size = matrix.length + 8
  const scale = Math.ceil(1000 / size)
  const canvas = document.createElement('canvas')
  canvas.width = size * scale
  canvas.height = size * scale
  const context = canvas.getContext('2d')
  if (!context) return
  context.fillStyle = '#fff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#000'
  matrix.forEach((row, y) =>
    row.forEach((dark, x) => {
      if (dark) context.fillRect((x + 4) * scale, (y + 4) * scale, scale, scale)
    }),
  )
  canvas.toBlob((blob) => {
    if (blob) downloadBlob(blob, fileName)
  }, 'image/png')
}
