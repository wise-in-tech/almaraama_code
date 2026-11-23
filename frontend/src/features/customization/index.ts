import { createRoute, type AnyRoute } from '@tanstack/react-router'
import { TextsAdminPage } from './routes/TextsAdminPage'

/** Signed-in routes of the customization feature: the dictionary of the interface texts (M6, ADR-029). */
export function customizationRoutes<TParent extends AnyRoute>(parent: TParent) {
  return [createRoute({ getParentRoute: () => parent, path: 'admin/texts', component: TextsAdminPage })] as const
}
