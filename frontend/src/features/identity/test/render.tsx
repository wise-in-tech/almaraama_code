import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
  type RouteComponent,
} from '@tanstack/react-router'
import { render } from '@testing-library/react'

/**
 * Renders one identity page at {@code path}, with stub pages for the places it may lead to (home, sign-in, forgotten
 * password, verification), so a test can check where the person ends up.
 */
export function renderPage(path: string, Page: RouteComponent) {
  const root = createRootRoute()
  const stubs = ['/', '/login', '/forgot-password', '/verify-email']
    .filter((stub) => stub !== path)
    .map((stub) =>
      createRoute({ getParentRoute: () => root, path: stub, component: () => <p>{`page ${stub}`}</p> }),
    )
  const page = createRoute({ getParentRoute: () => root, path, component: Page })
  const router = createRouter({
    routeTree: root.addChildren([page, ...stubs]),
    history: createMemoryHistory({ initialEntries: [path] }),
  })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}
