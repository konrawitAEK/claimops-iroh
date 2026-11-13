// Relative on purpose: nginx (Docker) and the dev-server proxy (ng serve) both forward /api to the backend,
// so the app and the API share one origin and no CORS or build-time URL is needed.
export const environment = {
  apiUrl: '/api',
};
