/**
 * Repository coordinates sent so the server knows which GitHub repo to use
 * for source fetching, issue creation and PR opening.
 */
export interface RepositoryInfo {
  owner: string;
  name: string;
  branch?: string;
}

/**
 * Universal exception report payload — matches the server's ExceptionReportRequest contract.
 */
export interface ExceptionReport {
  language: string;
  framework?: string;
  serviceName?: string;
  environment?: string;
  timestamp: string;
  threadName?: string;
  exception: ExceptionInfo;
  request?: RequestContext;
  fingerprint?: string;
  /** GitHub repository where this app's source code lives. */
  repository?: RepositoryInfo;
}

export interface ExceptionInfo {
  type: string;
  message?: string;
  rawStackTrace?: string;
  frames: StackFrame[];
}

/**
 * A single stack frame resolved by the browser stack parser.
 * {@code file} contains the URL or sourcemap-resolved path.
 */
export interface StackFrame {
  file: string;
  function?: string;
  line: number;
  column?: number;
  isProjectCode: boolean;
}

export interface RequestContext {
  method?: string;
  uri?: string;
  queryString?: string;
  headers?: Record<string, string>;
  body?: string;
  authenticatedUser?: string;
}

export interface VueSdkConfig {
  /** URL of the exception-intelligence-server. */
  serverUrl: string;
  /** Optional API key for server authentication. */
  apiKey?: string;
  /** Service / application name. */
  serviceName?: string;
  /** Deployment environment (production, staging, …). */
  environment?: string;
  /**
   * URL substrings that identify project code frames.
   * Frames from CDNs (unpkg.com, cdn.jsdelivr.net) or the Vue runtime are excluded.
   * Example: ['myapp.com', 'localhost']
   */
  projectUrls?: string[];
  /** Maximum events sent per minute (default: 5). */
  maxEventsPerMinute?: number;
  /**
   * GitHub repository where this app's source code lives.
   * The server uses this to fetch the source, create issues and PRs.
   */
  repository?: RepositoryInfo;
  /**
   * Callback invoked before sending — allows enriching the report
   * (e.g. adding authenticated user).
   */
  beforeSend?: (report: ExceptionReport) => ExceptionReport | null;
}
