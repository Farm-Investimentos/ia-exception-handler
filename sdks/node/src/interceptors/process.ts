import { ExceptionReport, SdkConfig } from '../types';
import { parseStackTrace } from '../parser/stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * Registers Node.js process-level error handlers:
 * - uncaughtException — synchronous errors not caught by any handler
 * - unhandledRejection — Promise rejections without a .catch()
 *
 * Usage:
 * ```ts
 * import { registerProcessHandlers } from '@exception-intelligence/sdk-node/interceptors/process';
 *
 * registerProcessHandlers({
 *   serverUrl: 'http://exception-intelligence-server:8090',
 *   serviceName: 'my-service',
 * });
 * ```
 */
export function registerProcessHandlers(config: SdkConfig): void {
  if (!config || !config.serverUrl) {
    console.warn('[exception-intelligence] registerProcessHandlers called without serverUrl — SDK disabled, no handlers registered.');
    return;
  }

  const rateLimiter = new RateLimiter(config.maxEventsPerMinute ?? 5);

  process.on('uncaughtException', (err: Error) => {
    // Node.js docs: after an uncaughtException the process may be in an
    // undefined state; the only safe action is to send the report and exit.
    // We schedule process.exit(1) immediately and give the fire-and-forget
    // send a best-effort chance to complete within 2 s.
    const exit = () => process.exit(1);
    const exitTimer = setTimeout(exit, 2000);
    exitTimer.unref?.(); // don't keep the event loop alive just for the timer

    if (rateLimiter.tryConsume()) {
      sendError(err, config, 'uncaughtException')
        .catch(() => undefined)
        .finally(exit);
    } else {
      exit();
    }
  });

  process.on('unhandledRejection', (reason: unknown) => {
    const err = reason instanceof Error ? reason : new Error(String(reason));
    if (rateLimiter.tryConsume()) {
      sendError(err, config, 'unhandledRejection').catch(() => undefined);
    }
  });
}

async function sendError(
  error: Error,
  config: SdkConfig,
  origin: string
): Promise<void> {
  const frames = parseStackTrace(error.stack ?? '', config.projectPaths ?? [], config.repoRoot);
  const fingerprint = computeFingerprint(error.constructor.name, frames);

  const report: ExceptionReport = {
    language: 'javascript',
    framework: config.framework ?? 'node',
    serviceName: config.serviceName,
    environment: config.environment,
    timestamp: new Date().toISOString(),
    threadName: origin,
    exception: {
      type: error.constructor.name,
      message: error.message,
      rawStackTrace: error.stack,
      frames,
    },
    fingerprint,
    repository: config.repository,
  };

  await sendReport(report, config);
}
