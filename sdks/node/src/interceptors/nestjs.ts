import { ExceptionReport, SdkConfig } from '../types';
import { parseStackTrace } from '../parser/stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * NestJS global exception filter factory.
 *
 * Usage (in main.ts):
 * ```ts
 * import { createNestJsExceptionFilter } from '@exception-intelligence/sdk-node/interceptors/nestjs';
 *
 * const app = await NestFactory.create(AppModule);
 * app.useGlobalFilters(createNestJsExceptionFilter({
 *   serverUrl: 'http://exception-intelligence-server:8090',
 *   serviceName: 'my-nest-service',
 *   projectPaths: ['/app/src'],
 * }));
 * ```
 *
 * Note: the returned object implements the NestJS ExceptionFilter interface
 * without importing @nestjs/common to avoid a hard dependency.
 */
export function createNestJsExceptionFilter(config: SdkConfig): NestExceptionFilter {
  const rateLimiter = new RateLimiter(config.maxEventsPerMinute ?? 5);
  return new ExceptionIntelligenceNestFilter(config, rateLimiter);
}

interface HttpArgumentsHost {
  getRequest<T = unknown>(): T;
  getResponse<T = unknown>(): T;
}

interface ArgumentsHost {
  switchToHttp(): HttpArgumentsHost;
}

interface NestExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void;
}

/** Minimal duck-typing for ServerResponse / express Response */
interface HttpResponse {
  status(code: number): this;
  json(body: unknown): this;
  headersSent?: boolean;
}

class ExceptionIntelligenceNestFilter implements NestExceptionFilter {
  constructor(
    private readonly config: SdkConfig,
    private readonly rateLimiter: RateLimiter
  ) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const error = exception instanceof Error ? exception : new Error(String(exception));
    const httpHost = host.switchToHttp();
    const req = httpHost.getRequest<Record<string, unknown>>();
    const res = httpHost.getResponse<HttpResponse>();

    // ── 1. Forward to server (rate-limited, fire-and-forget) ─────────────
    if (this.rateLimiter.tryConsume()) {
      const frames = parseStackTrace(error.stack ?? '', this.config.projectPaths ?? [], this.config.repoRoot);
      const fingerprint = computeFingerprint(error.constructor.name, frames);

      const report: ExceptionReport = {
        language: 'typescript',
        framework: this.config.framework ?? 'nestjs',
        serviceName: this.config.serviceName,
        environment: this.config.environment,
        timestamp: new Date().toISOString(),
        threadName: 'main',
        exception: {
          type: error.constructor.name,
          message: error.message,
          rawStackTrace: error.stack,
          frames,
        },
        request: {
          method: req['method'] as string | undefined,
          uri: req['url'] as string | undefined,
          body: req['body'] != null ? JSON.stringify(req['body']) : undefined,
        },
        fingerprint,
        repository: this.config.repository,
      };

      sendReport(report, this.config).catch(() => undefined);
    }

    // ── 2. Send HTTP error response (replaces NestJS built-in handler) ────
    if (!res.headersSent) {
      const status = (exception as { status?: number; statusCode?: number })?.status
        ?? (exception as { statusCode?: number })?.statusCode
        ?? 500;

      res.status(status).json({
        statusCode: status,
        message: error.message || 'Internal server error',
        timestamp: new Date().toISOString(),
        path: req['url'],
      });
    }
  }
}
