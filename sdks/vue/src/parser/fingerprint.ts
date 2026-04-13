import { StackFrame } from '../types';

/**
 * Computes a short fingerprint for deduplication.
 * Uses a simple djb2-style hash (no crypto API needed in browser context).
 */
export function computeFingerprint(errorType: string, frames: StackFrame[]): string {
  const top = frames.find(f => f.isProjectCode) ?? frames[0];
  const key = top
    ? `${errorType}:${top.function ?? top.file}:${top.line}`
    : errorType;

  let hash = 5381;
  for (let i = 0; i < key.length; i++) {
    hash = ((hash << 5) + hash) ^ key.charCodeAt(i);
  }
  return (hash >>> 0).toString(16).substring(0, 8);
}
