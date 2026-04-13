import { parseStackTrace } from '../browser-stack-parser';

const CHROME_STACK = [
  'TypeError: Cannot read property of undefined',
  '    at OrderComponent.submitOrder (https://myapp.com/app.js:142:15)',
  '    at async VueComponent.created (https://myapp.com/app.js:88:5)',
  '    at setupStatefulComponent (https://unpkg.com/vue@3/dist/vue.cjs.js:1234:5)',
  '    at https://cdn.jsdelivr.net/npm/vue/dist/vue.js:1:1',
].join('\n');

const FIREFOX_STACK = [
  'TypeError: undefined is not an object',
  'OrderComponent.submitOrder@https://myapp.com/app.js:142:15',
  'VueComponent.created@https://myapp.com/app.js:88:5',
  'setupStatefulComponent@https://unpkg.com/vue@3/dist/vue.cjs.js:1234:5',
].join('\n');

describe('parseStackTrace - Chrome', () => {
  it('parses Chrome frames', () => {
    const frames = parseStackTrace(CHROME_STACK, ['myapp.com']);
    expect(frames.length).toBeGreaterThan(0);
  });

  it('marks project frames', () => {
    const frames = parseStackTrace(CHROME_STACK, ['myapp.com']);
    const project = frames.filter(f => f.isProjectCode);
    expect(project.length).toBeGreaterThan(0);
    expect(project[0].file).toContain('myapp.com');
  });

  it('excludes CDN frames from project code', () => {
    const frames = parseStackTrace(CHROME_STACK, ['myapp.com']);
    const cdn = frames.find(f => f.file.includes('unpkg.com') || f.file.includes('jsdelivr.net'));
    expect(cdn?.isProjectCode).toBe(false);
  });

  it('excludes Vue runtime frames from project code', () => {
    const frames = parseStackTrace(CHROME_STACK, ['myapp.com']);
    const vueRuntime = frames.find(f => f.file.includes('vue.cjs'));
    expect(vueRuntime?.isProjectCode).toBe(false);
  });

  it('extracts correct line numbers', () => {
    const frames = parseStackTrace(CHROME_STACK, ['myapp.com']);
    const appFrame = frames.find(f => f.isProjectCode);
    expect(appFrame?.line).toBe(142);
  });
});

describe('parseStackTrace - Firefox', () => {
  it('parses Firefox format', () => {
    const frames = parseStackTrace(FIREFOX_STACK, ['myapp.com']);
    expect(frames.length).toBeGreaterThan(0);
  });

  it('marks project frames in Firefox format', () => {
    const frames = parseStackTrace(FIREFOX_STACK, ['myapp.com']);
    const project = frames.filter(f => f.isProjectCode);
    expect(project.length).toBeGreaterThan(0);
  });
});
