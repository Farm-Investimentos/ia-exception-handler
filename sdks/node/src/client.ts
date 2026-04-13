import { ExceptionReport, SdkConfig } from './types';

/**
 * Sends an exception report to the Exception Intelligence Server.
 * Fire-and-forget: failures are logged, never thrown.
 */
export async function sendReport(report: ExceptionReport, config: SdkConfig): Promise<void> {
  const url = `${config.serverUrl}/v1/exceptions`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (config.apiKey) {
    headers['X-API-Key'] = config.apiKey;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), config.timeoutMs ?? 5000);

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(report),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      console.warn(
        `[exception-intelligence] Server responded with ${response.status} for ${url}`
      );
    }
  } catch (err: unknown) {
    console.warn(
      `[exception-intelligence] Failed to reach server at ${url}:`,
      err instanceof Error ? err.message : String(err)
    );
  }
}

/**
 * Simple token-bucket rate limiter.
 */
export class RateLimiter {
  private count = 0;
  private windowStart = Date.now();

  constructor(private readonly maxPerMinute: number) {}

  tryConsume(): boolean {
    const now = Date.now();
    if (now - this.windowStart >= 60_000) {
      this.count = 0;
      this.windowStart = now;
    }
    if (this.count < this.maxPerMinute) {
      this.count++;
      return true;
    }
    return false;
  }
}
