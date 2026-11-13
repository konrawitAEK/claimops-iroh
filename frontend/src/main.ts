import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { connectIroh } from './app/core/iroh';

// Connect to the backend's iroh-gateway first, so the app's very first API call already goes over iroh.
connectIroh()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err: unknown) => {
    console.error(err);
    const root = document.querySelector('app-root');
    if (root) {
      root.textContent = `Can't reach the ClaimOps backend: ${err instanceof Error ? err.message : String(err)}`;
      root.classList.add('boot-error');
    }
  });
