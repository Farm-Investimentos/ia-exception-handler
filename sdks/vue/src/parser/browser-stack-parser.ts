import { StackFrame } from '../types';

/**
 * Multi-browser stack trace parser.
 *
 * Handles the three main formats:
 * - Chrome / V8:  "  at FunctionName (https://example.com/file.js:10:5)"
 * - Firefox:      "functionName@https://example.com/file.js:10:5"
 * - Safari:       "functionName@https://example.com/file.js:10:5" (same as Firefox)
 */

// Chrome / V8 format
const CHROME_RE = /^\s*at (?:async )?(.+?) \((.+):(\d+):(\d+)\)$/;
const CHROME_ANON_RE = /^\s*at (?:async )?(.+):(\d+):(\d+)$/;

// Firefox / Safari format
const FIREFOX_RE = /^(.+?)@(.+):(\d+):(\d+)$/;

// CDN / framework patterns to exclude
const VENDOR_PATTERNS = [
  'node_modules',
  'unpkg.com',
  'cdn.jsdelivr.net',
  'cdnjs.cloudflare.com',
  '/vue.esm',
  '/vue.cjs',
  '/vue.runtime',
  '@vue/runtime',
  '/chunk-',
  'webpack/runtime',
];

export function parseStackTrace(
  stack: string,
  projectUrls: string[] = []
): StackFrame[] {
  const lines = stack.split('\n');
  const frames: StackFrame[] = [];

  for (const line of lines) {
    const frame = parseLine(line.trim(), projectUrls);
    if (frame) frames.push(frame);
  }

  return frames;
}

function parseLine(line: string, projectUrls: string[]): StackFrame | null {
  // Skip the first line (the Error message itself)
  if (!line.includes(':') && !line.startsWith('at ')) return null;

  let match = CHROME_RE.exec(line);
  if (match) {
    const [, fn, file, rawLine, rawCol] = match;
    return buildFrame(fn, file, parseInt(rawLine, 10), parseInt(rawCol, 10), projectUrls);
  }

  match = CHROME_ANON_RE.exec(line);
  if (match) {
    const [, file, rawLine, rawCol] = match;
    return buildFrame(undefined, file, parseInt(rawLine, 10), parseInt(rawCol, 10), projectUrls);
  }

  match = FIREFOX_RE.exec(line);
  if (match) {
    const [, fn, file, rawLine, rawCol] = match;
    return buildFrame(fn || undefined, file, parseInt(rawLine, 10), parseInt(rawCol, 10), projectUrls);
  }

  return null;
}

function buildFrame(
  fn: string | undefined,
  file: string,
  line: number,
  column: number,
  projectUrls: string[]
): StackFrame {
  return {
    file,
    function: fn && fn !== 'anonymous' ? fn : undefined,
    line,
    column,
    isProjectCode: isProjectCode(file, projectUrls),
  };
}

function isProjectCode(file: string, projectUrls: string[]): boolean {
  if (VENDOR_PATTERNS.some(p => file.includes(p))) return false;
  if (file === '<anonymous>') return false;
  if (projectUrls.length === 0) return true;
  return projectUrls.some(u => file.includes(u));
}
