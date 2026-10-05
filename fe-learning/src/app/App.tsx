import { RouterProvider, createBrowserRouter } from 'react-router-dom';
import type { ReactElement } from 'react';

import { routes } from '@/app/routes';

/**
 * Data router instance. Created once at module scope so navigation state
 * survives re-renders of the provider tree.
 */
const router = createBrowserRouter(routes);

/** Application root: mounts the router and nothing else. */
export function App(): ReactElement {
  return <RouterProvider router={router} />;
}

export default App;