import { ExceptionReport, VueSdkConfig } from './types';

/**
 * Sends an exception report to the server.
 * Uses fetch() with a 5-second timeout. Falls back to sendBeacon() if the
 * page is unloading (so reports are not lost on navigation).
 */
export async function sendReport(report: ExceptionReport, config: VueSdkConfig): Promise<void> {
  const url = `${config.serverUrl}/v1/exceptions`;
  const body = JSON.stringify(report);

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (config.apiKey) {
    headers['X-API-Key'] = config.apiKey;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    const res = await fetch(url, {
      method: 'POST',
      headers,
      body,
      keepalive: true,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!res.ok) {
      console.warn(`[exception-intelligence] Server responded with ${res.status}`);
    }
  } catch (err: unknown) {
    // Fallback to beacon for page-unload scenarios
    if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
      navigator.sendBeacon(url, body);
    } else {
      console.warn('[exception-intelligence] Failed to send report:', err);
    }
  }
}

/**
 * Simple rate limiter compatible with browser environment (no crypto, no process).
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
