import { ExceptionReport, VueSdkConfig } from '../types';
import { parseStackTrace } from '../parser/browser-stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * Installs global browser error handlers:
 * - window.onerror — synchronous JS errors not caught by Vue's error handler
 * - window.onunhandledrejection — unhandled Promise rejections
 *
 * Called automatically by the Vue plugin. Can also be used standalone.
 */
export function installWindowHandlers(config: VueSdkConfig, rateLimiter: RateLimiter): void {
  window.addEventListener('error', (event: ErrorEvent) => {
    if (!rateLimiter.tryConsume()) return;
    const err = event.error instanceof Error
      ? event.error
      : new Error(event.message ?? 'Unknown error');

    captureAndSend(err, 'window.onerror', config).catch(() => undefined);
  });

  window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
    if (!rateLimiter.tryConsume()) return;
    const err = event.reason instanceof Error
      ? event.reason
      : new Error(String(event.reason ?? 'Unhandled Promise rejection'));

    captureAndSend(err, 'unhandledrejection', config).catch(() => undefined);
  });
}

export async function captureAndSend(
  err: Error,
  origin: string,
  config: VueSdkConfig
): Promise<void> {
  const frames = parseStackTrace(err.stack ?? '', config.projectUrls ?? []);
  const fingerprint = computeFingerprint(err.constructor.name, frames);

  let report: ExceptionReport | null = {
    language: 'javascript',
    framework: config.serviceName ? 'vue' : 'browser',
    serviceName: config.serviceName,
    environment: config.environment,
    timestamp: new Date().toISOString(),
    threadName: origin,
    exception: {
      type: err.constructor.name,
      message: err.message,
      rawStackTrace: err.stack,
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

  if (config.beforeSend) {
    report = config.beforeSend(report);
    if (!report) return;
  }

  await sendReport(report, config);
}
