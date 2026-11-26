import { useQuery } from '@tanstack/react-query'
import { Navigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { ErrorMessage, Loading } from '@/shared/ui'

/**
 * The page of an equipment label {@code /q/<code>} (G4, criterion 8): resolves the code for the signed-in person and
 * opens the report form prefilled with the equipment. An unknown, replaced or foreign code says so, without detail.
 */
export function QrCodePage() {
  const { t } = useTranslation()
  const { code } = useParams({ strict: false }) as { code: string }
  const asset = useQuery({
    queryKey: ['asset-qr', code],
    retry: false,
    queryFn: () => unwrap(api.GET('/v1/asset-qr/{code}', { params: { path: { code } } })),
  })
  if (asset.isPending) return <Loading />
  if (asset.error) return <ErrorMessage message={problemMessage(asset.error, t) || t('assets.qrUnknown')} />
  return <Navigate to="/sites/$siteId/report" params={{ siteId: asset.data.siteId }} search={{ assetId: asset.data.id }} replace />
}
