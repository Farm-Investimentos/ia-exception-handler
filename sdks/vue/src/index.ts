export { ExceptionIntelligencePlugin } from './plugin';
export { installVueErrorHandler } from './interceptors/vue-error';
export { installWindowHandlers, captureAndSend } from './interceptors/window-error';
export { parseStackTrace } from './parser/browser-stack-parser';
export { computeFingerprint } from './parser/fingerprint';
export { sendReport, RateLimiter } from './client';
export type { ExceptionReport, ExceptionInfo, StackFrame, RequestContext, VueSdkConfig } from './types';
