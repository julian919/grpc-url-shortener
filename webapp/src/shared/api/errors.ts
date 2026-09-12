import { z } from 'zod';

/**
 * The machine-readable reasons the backend attaches to auth failures, as a google.rpc.ErrorInfo
 * detail. Branch on these -- never on `message`, which is human-facing text the backend is free
 * to reword (AIP-193: ErrorInfo exists "so that users can write code against specific aspects of
 * the error").
 *
 * Access-token failures come back as 401 (UNAUTHENTICATED); refresh-token failures as 400,
 * because RFC 6749 calls that `invalid_grant`. That difference is deliberate: it keeps a failed
 * refresh from looking like "the access token expired", so a retry-on-401 client can't loop.
 */
export const ERROR_REASONS = [
  'ACCESS_TOKEN_MISSING',
  'ACCESS_TOKEN_EXPIRED',
  'ACCESS_TOKEN_INVALID',
  'REFRESH_TOKEN_MISSING',
  'REFRESH_TOKEN_INVALID',
  'REFRESH_TOKEN_REVOKED',
  'REFRESH_TOKEN_EXPIRED',
] as const;

export type ErrorReason = (typeof ERROR_REASONS)[number];

export function isErrorReason(value: unknown): value is ErrorReason {
  return typeof value === 'string' && (ERROR_REASONS as readonly string[]).includes(value);
}

/**
 * The JSON shape Envoy produces from a gRPC error when `convert_grpc_status` is on: the
 * google.rpc.Status from the grpc-status-details-bin trailer, rendered as the response body.
 * Parsed leniently -- a proxy-generated 502 won't have any of it.
 */
const errorInfoDetail = z.object({
  '@type': z.string(),
  reason: z.string().optional(),
  domain: z.string().optional(),
});

const rpcStatusSchema = z.object({
  code: z.number().optional(),
  message: z.string().optional(),
  details: z.array(z.looseObject({})).optional(),
});

/** A failed call to the edge, carrying the bits a caller can actually branch on. */
export class ApiError extends Error {
  constructor(
    readonly httpStatus: number,
    message: string,
    readonly reason?: ErrorReason | string,
    readonly grpcCode?: number
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** True when refreshing the access token could plausibly fix this. */
  get isExpiredAccessToken(): boolean {
    return this.reason === 'ACCESS_TOKEN_EXPIRED';
  }

  /** True when the refresh token itself is finished: clear the session and re-login. */
  get isDeadRefreshToken(): boolean {
    return typeof this.reason === 'string' && this.reason.startsWith('REFRESH_TOKEN_');
  }
}

/** Builds an ApiError from a non-2xx response body, pulling out ErrorInfo.reason if present. */
export function toApiError(httpStatus: number, body: unknown): ApiError {
  const parsed = rpcStatusSchema.safeParse(body);
  if (!parsed.success) {
    return new ApiError(httpStatus, `Request failed with HTTP ${httpStatus}`);
  }

  const { code, message, details } = parsed.data;
  const reason = details
    ?.map((detail) => errorInfoDetail.safeParse(detail))
    .find((result) => result.success && result.data['@type'].endsWith('google.rpc.ErrorInfo'))
    ?.data?.reason;

  return new ApiError(
    httpStatus,
    message ?? `Request failed with HTTP ${httpStatus}`,
    reason,
    code
  );
}
