/**
 * A failed call to the edge, in the shape a caller can branch on.
 *
 * Envoy's `convert_grpc_status` renders the gRPC error's google.rpc.Status as the response body,
 * so a failure arrives as JSON like:
 *
 * ```json
 * { "code": 16, "message": "Access token expired",
 *   "details": [ { "@type": "…/google.rpc.ErrorInfo",
 *                  "reason": "ACCESS_TOKEN_EXPIRED", "domain": "auth.example.com" } ] }
 * ```
 *
 * Branch on `reason`, never on `message` -- the message is human-facing text the backend is free
 * to reword, while ErrorInfo.reason exists precisely "so that users can write code against
 * specific aspects of the error" (AIP-193).
 *
 * Reasons the backend emits today: ACCESS_TOKEN_MISSING / _EXPIRED / _INVALID, and
 * REFRESH_TOKEN_MISSING / _INVALID / _REVOKED / _EXPIRED. Typed as `string` rather than a union,
 * because a reason this client has never heard of must still round-trip: the backend can add one
 * without the frontend being regenerated.
 *
 * Access-token failures come back as 401 (UNAUTHENTICATED); refresh-token failures as 400, because
 * RFC 6749 calls that `invalid_grant`. That difference is deliberate -- it keeps a failed refresh
 * from looking like "the access token expired", so a retry-on-401 client cannot loop.
 */
export class ApiError extends Error {
  constructor(
    readonly httpStatus: number,
    message: string,
    readonly reason?: string,
    readonly grpcCode?: number
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** True when refreshing the access token could plausibly fix this. */
  get isExpiredAccessToken(): boolean {
    return this.reason === 'ACCESS_TOKEN_EXPIRED';
  }

  /** True when the refresh token itself is finished: clear the session and send them to login. */
  get isDeadRefreshToken(): boolean {
    return this.reason?.startsWith('REFRESH_TOKEN_') ?? false;
  }
}

/**
 * Builds an ApiError from a non-2xx body, pulling out ErrorInfo.reason when it is there.
 *
 * Everything is optional on purpose: a proxy-generated 502 or a gateway timeout page has none of
 * this structure, and that must degrade to a plain "HTTP 502" rather than throwing while building
 * the error.
 */
export function toApiError(httpStatus: number, body: unknown): ApiError {
  const status = asRecord(body);
  const details = Array.isArray(status?.details) ? status.details : [];

  const errorInfo = asRecord(
    details.find((detail) => asRecord(detail)?.['@type']?.toString().endsWith('google.rpc.ErrorInfo'))
  );

  return new ApiError(
    httpStatus,
    typeof status?.message === 'string' ? status.message : `Request failed with HTTP ${httpStatus}`,
    typeof errorInfo?.reason === 'string' ? errorInfo.reason : undefined,
    typeof status?.code === 'number' ? status.code : undefined
  );
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null
    ? (value as Record<string, unknown>)
    : undefined;
}
