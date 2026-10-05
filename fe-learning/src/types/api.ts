/** Transport-level contracts shared by every feature slice. */

/**
 * Success/error envelope emitted by the Spring Boot `GlobalExceptionHandler`.
 *
 * Mirrors `com.thesis.elearning.common.dto.ApiResponse` on the server. Every
 * endpoint - including those that fail - answers with this shape, so the client
 * never has to infer the body type from the HTTP status.
 *
 * `data` is the only field feature code reads on success; `message` and
 * `fieldErrors` are the two the error path reads. `unwrapEnvelope` in
 * `apiClient.ts` is the only place this type is consumed directly.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  /** User-facing Vietnamese text, safe to render as-is. */
  message: string;
  timestamp: string;
  /** Stable machine-readable code, e.g. `RESOURCE_NOT_FOUND`. */
  errorCode?: string;
  /** Property path to message, e.g. `{ username: "[Tên đăng nhập] ..." }`. */
  fieldErrors?: Record<string, string>;
}

/** Error envelope emitted by the Spring Boot `@RestControllerAdvice`. */
export interface ApiErrorPayload {
  status: number;
  error: string;
  message: string;
  /** Field-level validation failures keyed by property path. */
  fieldErrors?: Record<string, string>;
  traceId?: string;
  timestamp?: string;
}

/** Normalised error thrown by the Axios interceptors. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

/** Standard Spring `Page<T>` projection. */
export interface Page<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Query parameters accepted by every paginated collection endpoint. */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
}