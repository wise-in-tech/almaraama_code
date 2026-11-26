import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { AssetPage } from './routes/AssetPage'
import { AssetsPage } from './routes/AssetsPage'
import { QrCodePage } from './routes/QrCodePage'

/**
 * Signed-in routes of the assets feature (G1 to G4, criterion 8): the equipment of a residence with creation, import
 * and labels; one equipment with its characteristics, documents, warranties and QR code; and the page of a label,
 * which opens the prefilled report. A label scanned without session goes through sign-in first.
 */
export function assetsRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [
    createRoute({ getParentRoute: () => parent, path: 'sites/$siteId/assets', component: AssetsPage }),
    createRoute({ getParentRoute: () => parent, path: 'assets/$assetId', component: AssetPage }),
    createRoute({ getParentRoute: () => parent, path: 'q/$code', component: QrCodePage }),
  ] as const
}
