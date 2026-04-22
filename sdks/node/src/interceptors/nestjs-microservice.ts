import { ExceptionReport, SdkConfig } from '../types';
import { parseStackTrace } from '../parser/stack-parser';
import { computeFingerprint } from '../parser/fingerprint';
import { sendReport, RateLimiter } from '../client';

/**
 * NestJS microservice (RPC) global exception filter factory.
 *
 * Usage (in main.ts of a Nest microservice app):
 * ```ts
 * import { createNestJsMicroserviceFilter } from '@farm-investimentos/exception-intelligence-sdk-node';
 *
 * const app = await NestFactory.createMicroservice(AppModule, { transport: Transport.TCP });
 * app.useGlobalFilters(createNestJsMicroserviceFilter({
 *   serverUrl: 'http://exception-intelligence-server:8090',
 *   serviceName: 'my-microservice',
 * }));
 * ```
 *
 * The returned filter implements the RpcExceptionFilter contract without
 * importing @nestjs/common or rxjs as hard dependencies. It tries to emit the
 * exception through rxjs.throwError() if rxjs is available at runtime (which
 * it is in any real Nest app), otherwise re-throws synchronously.
 */
export function createNestJsMicroserviceFilter(config: SdkConfig): NestRpcExceptionFilter {
  if (!config || !config.serverUrl) {
    console.warn(
      '[exception-intelligence] NestJS microservice filter created without serverUrl — SDK disabled (pass-through).'
    );
    return new DisabledMicroserviceFilter();
  }
  const rateLimiter = new RateLimiter(config.maxEventsPerMinute ?? 5);
  return new ExceptionIntelligenceMicroserviceFilter(config, rateLimiter);
}

interface NestRpcExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): unknown;
}

interface RpcArgumentsHost {
  getData<T = unknown>(): T;
  getContext<T = unknown>(): T;
}

interface ArgumentsHost {
  switchToRpc(): RpcArgumentsHost;
}

interface RxjsLike {
  throwError(factory: () => unknown): unknown;
}

function emitOrThrow(exception: unknown): unknown {
  // Align with Nest's BaseRpcExceptionFilter: when the thrown value is a
  // RpcException, emit its inner error (getError()) — not the wrapper
  // itself. Otherwise the wrapper's serialization would hide the inner
  // error one level deep (e.g. { error: { status: 404, ... } }), which
  // upstream gateways typically can't parse.
  const payload = unwrapRpcException(exception);

  // Attempt rxjs.throwError so Nest's RPC pipeline handles the error
  // through the normal observable contract. If rxjs isn't available
  // (shouldn't happen in a real Nest app), re-throw and let the
  // framework's fallback take over.
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const rxjs = require('rxjs') as RxjsLike;
    return rxjs.throwError(() => payload);
  } catch {
    throw payload;
  }
}

/**
 * Duck-typed check for @nestjs/microservices' RpcException. We can't
 * `instanceof` it without making @nestjs/microservices a hard dependency
 * of the SDK, so we detect it structurally via its `getError()` method.
 */
function unwrapRpcException(exception: unknown): unknown {
  if (
    exception != null &&
    typeof exception === 'object' &&
    typeof (exception as { getError?: unknown }).getError === 'function'
  ) {
    try {
      return (exception as { getError: () => unknown }).getError();
    } catch {
      return exception;
    }
  }
  return exception;
}

function extractPattern(host: ArgumentsHost): string | undefined {
  try {
    const rpc = host.switchToRpc();
    const ctx = rpc.getContext<Record<string, unknown>>();
    const getPattern = (ctx as { getPattern?: () => unknown }).getPattern;
    if (typeof getPattern === 'function') {
      const p = getPattern.call(ctx);
      return typeof p === 'string' ? p : JSON.stringify(p);
    }
  } catch {
    // ignore — context may be absent in tests
  }
  return undefined;
}

class ExceptionIntelligenceMicroserviceFilter implements NestRpcExceptionFilter {
  constructor(
    private readonly config: SdkConfig,
    private readonly rateLimiter: RateLimiter
  ) {}

  catch(exception: unknown, host: ArgumentsHost): unknown {
    const error = exception instanceof Error ? exception : new Error(String(exception));

    // Wrapped in try-catch so that any internal SDK failure (e.g. in
    // parseStackTrace, computeFingerprint, extractPattern) never prevents
    // the exception from propagating through the RPC pipeline below.
    if (this.rateLimiter.tryConsume()) {
      try {
        const frames = parseStackTrace(error.stack ?? '', this.config.projectPaths ?? [], this.config.repoRoot);
        const fingerprint = computeFingerprint(error.constructor.name, frames);
        const pattern = extractPattern(host);

        const report: ExceptionReport = {
          language: 'typescript',
          framework: this.config.framework ?? 'nestjs-microservice',
          serviceName: this.config.serviceName,
          environment: this.config.environment,
          timestamp: new Date().toISOString(),
          threadName: pattern ?? 'main',
          exception: {
            type: error.constructor.name,
            message: error.message,
            rawStackTrace: error.stack,
            frames,
          },
          fingerprint,
          repository: this.config.repository,
        };

        sendReport(report, this.config).catch(() => undefined);
      } catch (sdkErr: unknown) {
        console.warn(
          '[exception-intelligence] Internal error while building report:',
          sdkErr instanceof Error ? sdkErr.message : String(sdkErr)
        );
      }
    }

    return emitOrThrow(exception);
  }
}

class DisabledMicroserviceFilter implements NestRpcExceptionFilter {
  catch(exception: unknown, _host: ArgumentsHost): unknown {
    return emitOrThrow(exception);
  }
}
