import { StackFrame } from '../types';

/**
 * Parses V8 / Node.js stack trace strings into structured {@link StackFrame} objects.
 *
 * V8 frame formats:
 *   at Object.method (file:///path/to/file.js:10:5)
 *   at Object.method (/abs/path/file.ts:10:5)
 *   at async Promise.all (index 0)
 */
const V8_FRAME_RE = /^\s*at (?:async )?(.+?) \((.+):(\d+):(\d+)\)$/;
const V8_ANON_RE  = /^\s*at (?:async )?(.+):(\d+):(\d+)$/;

export function parseStackTrace(
  stack: string,
  projectPaths: string[] = [],
  repoRoot?: string
): StackFrame[] {
  // Resolve repo root: explicit config → process.cwd() as fallback
  const root = (repoRoot ?? process.cwd()).replace(/\/+$/, ''); // strip trailing slash

  const lines = stack.split('\n');
  const frames: StackFrame[] = [];

  for (const line of lines) {
    const frame = parseLine(line.trim(), projectPaths, root);
    if (frame) frames.push(frame);
  }

  return frames;
}

function parseLine(line: string, projectPaths: string[], repoRoot: string): StackFrame | null {
  let match = V8_FRAME_RE.exec(line);
  if (match) {
    const [, fn, rawFile, rawLine, rawCol] = match;
    const file = normalizeFile(rawFile, repoRoot);
    return {
      file,
      function: fn,
      line: parseInt(rawLine, 10),
      column: parseInt(rawCol, 10),
      isProjectCode: isProject(file, projectPaths, repoRoot),
    };
  }

  match = V8_ANON_RE.exec(line);
  if (match) {
    const [, rawFile, rawLine, rawCol] = match;
    const file = normalizeFile(rawFile, repoRoot);
    return {
      file,
      function: undefined,
      line: parseInt(rawLine, 10),
      column: parseInt(rawCol, 10),
      isProjectCode: isProject(file, projectPaths, repoRoot),
    };
  }

  return null;
}

/**
 * Converts an absolute path to a repo-relative path.
 *
 * "/abs/repo/src/service.ts" + repoRoot="/abs/repo" → "src/service.ts"
 */
function normalizeFile(raw: string, repoRoot: string): string {
  let file = raw.replace(/^file:\/\//, ''); // strip file:// protocol
  if (file.startsWith(repoRoot + '/')) {
    file = file.slice(repoRoot.length + 1); // make repo-relative
  }
  return file;
}

function isProject(file: string, projectPaths: string[], repoRoot: string): boolean {
  if (file.includes('node_modules')) return false;
  if (file.startsWith('node:')) return false;
  if (file.startsWith('<')) return false; // <anonymous>, <eval>

  if (projectPaths.length === 0) return true;

  return projectPaths.some(p => {
    // Compare both absolute and repo-relative forms
    const relative = p.startsWith(repoRoot + '/') ? p.slice(repoRoot.length + 1) : p;
    return file.startsWith(p) || file.startsWith(relative);
  });
}

/**
 * Returns the first project frame, or the first frame overall as fallback.
 */
export function topFrame(frames: StackFrame[]): StackFrame | undefined {
  return frames.find(f => f.isProjectCode) ?? frames[0];
}
