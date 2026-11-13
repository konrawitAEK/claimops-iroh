import { createIrohFetch } from 'iroh-fetch';
import { API_URL } from './api';

/**
 * Where the backend's iroh-gateway is. Read at startup from `/iroh.json`, which the Docker image writes from its
 * environment (see docker-entrypoint.d/40-iroh-config.sh); for `ng serve`, put one in `public/iroh.json`.
 */
interface IrohConfig {
  /** The gateway's endpoint id, as printed by iroh-gateway (`IROH_ENDPOINT_ID=...`). */
  endpointId?: string;
  /** The relay the gateway is on; optional, makes the first connection faster. */
  relayUrl?: string;
  /** Your own relay(s) for the browser to use, instead of n0's public relays. */
  clientRelayUrl?: string;
  /** Token for a relay that requires one. */
  relayToken?: string;
}

/**
 * Routes the app's API calls (`/api/**` on this origin) to the backend over iroh, so the backend needs no public
 * address. Angular's HttpClient uses `fetch` (`withFetch()`), and `install()` swaps the global `fetch`, so services
 * and pages are unchanged. Everything else (the app's own files) still loads normally.
 *
 * Returns false when no endpoint id is configured; API calls then go to `/api` on this origin as before
 * (the `ng serve` proxy), which is handy for development.
 */
export async function connectIroh(): Promise<boolean> {
  const config = await loadConfig();
  if (!config.endpointId) return false;

  const iroh = await withWasmFromAssets(() =>
    createIrohFetch({
      endpointIds: [{ endpointId: config.endpointId!, relayUrl: config.relayUrl || undefined }],
      relayUrl: config.clientRelayUrl || undefined,
      relayToken: config.relayToken || undefined,
      connectTimeout: 30_000,
    }),
  );
  const apiPath = new URL(API_URL + '/', location.href).pathname;
  iroh.install({
    match: (url) => url.origin === location.origin && url.pathname.startsWith(apiPath),
    websocket: false,
    eventSource: false,
  });
  return true;
}

/** Where angular.json copies iroh-fetch's WebAssembly client (`assets`). */
const WASM_URL = '/wasm/web_client_bg.wasm';

/**
 * iroh-fetch locates its .wasm file relative to its own module (`new URL(..., import.meta.url)`). The production
 * build happens to put it at WASM_URL, but `ng serve` serves modules from virtual paths where that lookup 404s.
 * So while the client starts, send that one request to the copy angular.json puts at WASM_URL, in both modes.
 */
async function withWasmFromAssets<T>(start: () => Promise<T>): Promise<T> {
  const realFetch = globalThis.fetch;
  globalThis.fetch = (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    return realFetch(url.endsWith('/web_client_bg.wasm') ? WASM_URL : input, init);
  };
  try {
    return await start();
  } finally {
    globalThis.fetch = realFetch;
  }
}

async function loadConfig(): Promise<IrohConfig> {
  try {
    const res = await fetch('/iroh.json', { cache: 'no-store' });
    // A missing file comes back as index.html (SPA fallback), so check the type before parsing.
    if (!res.ok || !res.headers.get('content-type')?.includes('json')) return {};
    const config = (await res.json()) as IrohConfig;
    return { ...config, endpointId: config.endpointId?.trim() };
  } catch {
    return {};
  }
}
