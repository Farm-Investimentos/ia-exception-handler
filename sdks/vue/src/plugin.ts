import type { App } from 'vue';
import { VueSdkConfig } from './types';
import { installVueErrorHandler } from './interceptors/vue-error';
import { installWindowHandlers } from './interceptors/window-error';
import { RateLimiter } from './client';

/**
 * Vue 3 plugin for Exception Intelligence.
 *
 * Usage:
 * ```ts
 * import { createApp } from 'vue';
 * import { ExceptionIntelligencePlugin } from '@exception-intelligence/sdk-vue';
 * import App from './App.vue';
 *
 * const app = createApp(App);
 *
 * app.use(ExceptionIntelligencePlugin, {
 *   serverUrl: 'https://exception-intelligence-server.mycompany.com',
 *   serviceName: 'my-frontend-app',
 *   environment: 'production',
 *   projectUrls: ['myapp.com', 'localhost'],
 * });
 *
 * app.mount('#app');
 * ```
 */
export const ExceptionIntelligencePlugin = {
  install(app: App, config: VueSdkConfig): void {
    if (!config || !config.serverUrl) {
      console.warn('[exception-intelligence] Plugin installed without serverUrl — disabled.');
      return;
    }

    const rateLimiter = new RateLimiter(config.maxEventsPerMinute ?? 5);

    // Vue application-level errors (component lifecycle, render, watchers)
    installVueErrorHandler(app, config, rateLimiter);

    // Browser-level unhandled errors and rejected promises
    installWindowHandlers(config, rateLimiter);
  },
};
