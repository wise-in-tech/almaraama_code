import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { QrCode, qrMatrix, qrSvg } from './QrCode'

describe('registration QR code (D2, criterion 4, ADR-034)', () => {
  it('criterion4_theQrCodeIsDrawnByTheBrowserAsASquareMatrix', () => {
    const matrix = qrMatrix('https://app.example.dz/r#K7QM4XPA2R')
    expect(matrix.length).toBeGreaterThanOrEqual(21)
    expect(matrix.every((row) => row.length === matrix.length)).toBe(true)
    expect(qrSvg(matrix)).toMatch(/^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg"/)
  })

  it('D2_theQrCodeIsAnImageWithALabel', () => {
    render(<QrCode text="https://app.example.dz/r#K7QM4XPA2R" label="QR code de la résidence" />)
    expect(screen.getByRole('img', { name: 'QR code de la résidence' })).toBeInTheDocument()
  })
})
