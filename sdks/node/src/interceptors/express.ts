import type { Request, Response, NextFunction, ErrorRequestHandler } from 'express';
import { ExceptionReport, RequestContext, SdkConfig } from '../types';
import { parseStackTrace } from '../parser/stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * Express error-handling middleware.
 *
 * Usage:
 * ```ts
 * import { createExpressErrorHandler } from '@exception-intelligence/sdk-node/interceptors/express';
 *
 * app.use(createExpressErrorHandler({
 *   serverUrl: 'http://exception-intelligence-server:8090',
 *   serviceName: 'my-service',
 *   projectPaths: ['/app/src'],
 * }));
 * ```
 *
 * Must be registered **after** all routes and other middleware.
 */
export function createExpressErrorHandler(config: SdkConfig): ErrorRequestHandler {
  if (!config || !config.serverUrl) {
    console.warn('[exception-intelligence] Express handler created without serverUrl — SDK disabled (pass-through).');
    return (err: unknown, _req: Request, _res: Response, next: NextFunction) => next(err);
  }

  const rateLimiter = new RateLimiter(config.maxEventsPerMinute ?? 5);

  return (err: unknown, req: Request, _res: Response, next: NextFunction) => {
    if (rateLimiter.tryConsume()) {
      captureAndSend(err, req, config).catch(() => undefined);
    }
    next(err);
  };
}

async function captureAndSend(err: unknown, req: Request, config: SdkConfig): Promise<void> {
  const error = err instanceof Error ? err : new Error(String(err));
  const frames = parseStackTrace(error.stack ?? '', config.projectPaths ?? [], config.repoRoot);
  const fingerprint = computeFingerprint(error.constructor.name, frames);

  const requestCtx: RequestContext = {
    method: req.method,
    uri: req.path,
    queryString: req.url.includes('?') ? req.url.split('?')[1] : undefined,
    headers: sanitizeHeaders(req.headers as Record<string, string>),
    body: typeof req.body === 'string' ? req.body : JSON.stringify(req.body),
    authenticatedUser: (req as unknown as Record<string, unknown>)['user']
      ? String((req as unknown as Record<string, unknown>)['user'])
      : undefined,
  };

  const report: ExceptionReport = {
    language: 'javascript',
    framework: config.framework ?? 'express',
    serviceName: config.serviceName,
    environment: config.environment,
    timestamp: new Date().toISOString(),
    threadName: 'main',
    exception: {
      type: error.constructor.name,
      message: error.message,
      rawStackTrace: error.stack,
      frames,
    },
    request: requestCtx,
    fingerprint,
    repository: config.repository,
  };

  await sendReport(report, config);
}

const SENSITIVE_HEADERS = new Set(['authorization', 'cookie', 'set-cookie', 'x-api-key']);

function sanitizeHeaders(headers: Record<string, string>): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers)) {
    result[key] = SENSITIVE_HEADERS.has(key.toLowerCase()) ? '[REDACTED]' : value;
  }
  return result;
}
