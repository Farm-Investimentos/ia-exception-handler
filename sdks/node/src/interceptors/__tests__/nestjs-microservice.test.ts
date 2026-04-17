import { createNestJsMicroserviceFilter } from '../nestjs-microservice';
import type { SdkConfig } from '../../types';

// Mock client to avoid real network calls
jest.mock('../../client', () => ({
  sendReport: jest.fn().mockResolvedValue(undefined),
  RateLimiter: jest.fn().mockImplementation(() => ({ tryConsume: () => true })),
}));

const { sendReport } = jest.requireMock('../../client');

const baseConfig: SdkConfig = {
  serverUrl: 'http://localhost:8090',
  serviceName: 'test-ms',
  environment: 'test',
};

function makeHost(pattern?: string) {
  return {
    switchToRpc: () => ({
      getData: <T>() => ({}) as T,
      getContext: <T>() =>
        ({
          getPattern: pattern != null ? () => pattern : undefined,
        }) as T,
    }),
  };
}

describe('createNestJsMicroserviceFilter', () => {
  beforeEach(() => {
    (sendReport as jest.Mock).mockClear();
  });

  it('returns a disabled (pass-through) filter when serverUrl is missing', () => {
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const filter = createNestJsMicroserviceFilter({ ...baseConfig, serverUrl: '' });
    expect(filter).toBeDefined();
    expect(warnSpy).toHaveBeenCalledWith(
      expect.stringContaining('SDK disabled')
    );
    warnSpy.mockRestore();
  });

  it('captures an exception and forwards it to sendReport with the RPC pattern as threadName', () => {
    const filter = createNestJsMicroserviceFilter(baseConfig);
    const host = makeHost('auth.user.find');
    const error = new Error('boom');

    // The filter's catch returns something (observable or throws). We don't
    // care about the return shape here — just that sendReport was invoked.
    try {
      filter.catch(error, host);
    } catch {
      // expected fallback when rxjs is absent
    }

    expect(sendReport).toHaveBeenCalledTimes(1);
    const [report] = (sendReport as jest.Mock).mock.calls[0];
    expect(report.framework).toBe('nestjs-microservice');
    expect(report.threadName).toBe('auth.user.find');
    expect(report.exception.type).toBe('Error');
    expect(report.exception.message).toBe('boom');
  });

  it('falls back to threadName=main when RPC pattern is not available', () => {
    const filter = createNestJsMicroserviceFilter(baseConfig);
    const host = makeHost(undefined);
    try {
      filter.catch(new Error('no-pattern'), host);
    } catch {
      // ignore
    }

    const [report] = (sendReport as jest.Mock).mock.calls[0];
    expect(report.threadName).toBe('main');
  });

  it('wraps non-Error throwables as Error before reporting', () => {
    const filter = createNestJsMicroserviceFilter(baseConfig);
    try {
      filter.catch('string error' as unknown, makeHost());
    } catch {
      // ignore
    }

    const [report] = (sendReport as jest.Mock).mock.calls[0];
    expect(report.exception.type).toBe('Error');
    expect(report.exception.message).toBe('string error');
  });
});
