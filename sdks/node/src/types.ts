/**
 * Repository coordinates sent by the SDK so the server knows which GitHub repo
 * to fetch source code from, create issues in and open PRs against.
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
  /** GitHub repository where this service's source code lives. */
  repository?: RepositoryInfo;
}

export interface ExceptionInfo {
  type: string;
  message?: string;
  rawStackTrace?: string;
  frames: StackFrame[];
}

/**
 * A single resolved stack frame.
 * {@code file} is a relative path within the project repository
 * (e.g. {@code src/services/order.service.ts}).
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

export interface SdkConfig {
  /** URL of the exception-intelligence-server. */
  serverUrl: string;
  /** Optional API key for server authentication. */
  apiKey?: string;
  /** Service name included in all reports. */
  serviceName?: string;
  /** Deployment environment (production, staging, …). */
  environment?: string;
  /** Framework identifier forwarded to the server. */
  framework?: string;
  /**
   * Path prefixes considered "project code" for frame filtering.
   * Frames under node_modules are always excluded.
   * Example: ["/app/src", "/home/user/project/src"]
   */
  projectPaths?: string[];
  /**
   * Absolute path to the repository root.
   * Used to convert absolute file paths in stack traces to repository-relative paths
   * before sending to the server (e.g. "/app/src/service.ts" → "src/service.ts").
   * Defaults to process.cwd() when not set.
   */
  repoRoot?: string;
  /** Maximum events sent per minute (default: 5). */
  maxEventsPerMinute?: number;
  /** Connection timeout in ms (default: 5000). */
  timeoutMs?: number;
  /**
   * GitHub repository where this service's source code lives.
   * The server uses this to fetch the source file that caused the exception,
   * create issues and open PRs in the correct repository.
   */
  repository?: RepositoryInfo;
}
