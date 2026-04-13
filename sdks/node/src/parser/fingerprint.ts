import { StackFrame } from '../types';
import { createHash } from 'crypto';

/**
 * Computes a short fingerprint string from exception type + top frame location.
 * Used by the server for deduplication.
 */
export function computeFingerprint(errorType: string, frames: StackFrame[]): string {
  const top = frames.find(f => f.isProjectCode) ?? frames[0];
  const key = top
    ? `${errorType}:${top.function ?? top.file}:${top.line}`
    : errorType;

  return createHash('sha1').update(key).digest('hex').substring(0, 8);
}
