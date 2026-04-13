import { parseStackTrace, topFrame } from '../stack-parser';

describe('parseStackTrace', () => {
  const V8_STACK = [
    'Error: something went wrong',
    '    at OrderService.create (/app/src/services/order.service.js:42:10)',
    '    at async Router.handle (node_modules/express/lib/router/index.js:284:15)',
    '    at async Promise.all (index 0)',
    '    at node:internal/process/task_queues:140:5',
  ].join('\n');

  it('parses V8 frames', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    expect(frames.length).toBeGreaterThan(0);
  });

  it('marks project code correctly', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    const projectFrames = frames.filter(f => f.isProjectCode);
    expect(projectFrames.length).toBeGreaterThan(0);
    expect(projectFrames[0].file).toContain('/app/src');
  });

  it('excludes node_modules frames from project code', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    const expressFrame = frames.find(f => f.file.includes('node_modules'));
    expect(expressFrame?.isProjectCode).toBe(false);
  });

  it('excludes node: internal frames from project code', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    const internalFrame = frames.find(f => f.file.startsWith('node:'));
    expect(internalFrame?.isProjectCode).toBe(false);
  });

  it('extracts function name', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    expect(frames[0].function).toBe('OrderService.create');
  });

  it('extracts line number', () => {
    const frames = parseStackTrace(V8_STACK, ['/app/src']);
    expect(frames[0].line).toBe(42);
  });

  it('returns all frames when no projectPaths given', () => {
    const frames = parseStackTrace(V8_STACK);
    const nodeInternal = frames.find(f => f.file.startsWith('node:'));
    // node: internal should still be excluded (never project code)
    expect(nodeInternal?.isProjectCode).toBe(false);
  });
});

describe('topFrame', () => {
  it('returns first project frame', () => {
    const frames = parseStackTrace(
      [
        'Error: test',
        '    at MyService.method (/app/src/service.js:10:5)',
        '    at lib (/node_modules/lib/index.js:1:1)',
      ].join('\n'),
      ['/app/src']
    );
    const top = topFrame(frames);
    expect(top?.file).toContain('/app/src/service.js');
  });
});
