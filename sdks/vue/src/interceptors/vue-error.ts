import type { App, ComponentPublicInstance } from 'vue';
import { VueSdkConfig } from '../types';
import { parseStackTrace } from '../parser/browser-stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * Installs the Vue application-level error handler.
 *
 * {@code app.config.errorHandler} captures errors from:
 * - Component render functions
 * - Lifecycle hooks (created, mounted, updated, …)
 * - Event handlers within components
 * - Watchers and computed properties
 */
export function installVueErrorHandler(
  app: App,
  config: VueSdkConfig,
  rateLimiter: RateLimiter
): void {
  app.config.errorHandler = (
    err: unknown,
    instance: ComponentPublicInstance | null,
    info: string
  ) => {
    if (!rateLimiter.tryConsume()) return;

    // Wrapped in try-catch: any SDK internal failure must never interfere
    // with Vue's normal error reporting or component lifecycle.
    try {
      const error = err instanceof Error ? err : new Error(String(err));
      const frames = parseStackTrace(error.stack ?? '', config.projectUrls ?? []);
      const fingerprint = computeFingerprint(error.constructor.name, frames);

      const componentName = resolveComponentName(instance);

      let report = {
        language: 'javascript',
        framework: 'vue',
        serviceName: config.serviceName,
        environment: config.environment,
        timestamp: new Date().toISOString(),
        threadName: `vue:${info}`,
        exception: {
          type: error.constructor.name,
          message: `[${componentName ?? 'Component'}] ${error.message}`,
          rawStackTrace: error.stack,
          frames,
        },
        request: {
          uri: window.location.href,
          queryString: window.location.search.replace(/^\?/, '') || undefined,
          headers: {
            'User-Agent': navigator.userAgent,
          },
        },
        fingerprint,
        repository: config.repository,
      };

      const final = config.beforeSend ? config.beforeSend(report) : report;
      if (!final) return;

      sendReport(final, config).catch(() => undefined);
    } catch (sdkErr: unknown) {
      // SDK internal error — log and move on; do not re-throw so Vue can
      // continue handling the original error normally.
      console.warn(
        '[exception-intelligence] Internal error in Vue error handler:',
        sdkErr instanceof Error ? sdkErr.message : String(sdkErr)
      );
    }
  };
}

function resolveComponentName(instance: ComponentPublicInstance | null): string | null {
  if (!instance) return null;
  const options = instance.$options;
  return (options && (options.name ?? options.__name)) ?? null;
}
