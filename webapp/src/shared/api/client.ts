import 'server-only';

import { fromJson, type JsonValue } from '@bufbuild/protobuf';

import { OAuth2TokenSchema } from '@/shared/api/gen/auth/api/auth_api_pb';
import { serverEnv } from '@/shared/config/env';
import { ApiError, toApiError } from '@/shared/api/errors';
import { logger } from '@/shared/observability/logger';

/**
 * Everything this app needs to talk to the Envoy edge, in one module.
 *
 * Read it top to bottom: the public entry point, then the transport, then the app's own token.
 * They live together because the token request is itself an edge call -- splitting them into
 * separate modules only forces an import cycle that has to be worked around.
 *
 * `server-only` means a client component importing this is a build error, which is what keeps the
 * client secret on the server.
 */

/**
 * Which credential a call travels with. The same split Cognixus's session manager makes per call
 * with `TokenCallerType.Client` / `.User`:
 *
 * - `'none'`   — public endpoints (login, the token endpoints themselves)
 * - `'client'` — this app's own identity; used for public reads and for register
 * - `{ accessToken }` — a signed-in person. Passed per call, never stashed in module state:
 *   on a server that state would be shared across everyone's requests.
 */
export type Auth = 'none' | 'client' | { accessToken: string };

export async function callApi(
  path: string,
  options: { method?: 'GET' | 'POST' | 'PATCH'; body?: unknown; auth: Auth }
): Promise<unknown> {
  const { method = 'GET', body, auth } = options;

  try {
    // `return await`, not `return`: without the await, a rejection would skip this catch.
    return await request(path, {
      method,
      body,
      headers: await authHeaders(auth),
    });
  } catch (error) {
    // The cache predicts expiry with THIS server's clock, but the backend judges it with its own.
    // A clock jump (e.g. the Docker VM resyncing after the laptop sleeps) can expire a token the
    // cache still trusts. When the backend says so, its verdict wins: drop the cached token and
    // retry ONCE with a fresh one. Only the app's own token can be replaced like this -- a user's
    // expired token needs the refresh flow, not a silent swap.
    if (
      auth === 'client' &&
      error instanceof ApiError &&
      error.isExpiredAccessToken
    ) {
      cached = undefined;
      return request(path, { method, body, headers: await authHeaders(auth) });
    }
    throw error;
  }
}

async function authHeaders(auth: Auth): Promise<Record<string, string>> {
  if (auth === 'client')
    return { Authorization: `Bearer ${await getClientToken()}` };
  if (typeof auth === 'object')
    return { Authorization: `Bearer ${auth.accessToken}` };
  return {};
}

// --- transport -------------------------------------------------------------------------------

/**
 * Give up on the edge after this long. Without it a hung upstream hangs the render forever: fetch
 * has no default timeout.
 */
const REQUEST_TIMEOUT_MS = 10_000;

/**
 * The only place that calls fetch. Returns the parsed body as `unknown` on purpose -- the caller
 * narrows it with the generated proto schema, which is the only thing that should decide shape.
 */
async function request(
  path: string,
  options: { method: string; body?: unknown; headers: Record<string, string> }
): Promise<unknown> {
  const { method, body, headers } = options;

  let response: Response;
  try {
    response = await fetch(`${serverEnv().API_BASE_URL}${path}`, {
      method,
      headers: {
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      // Tokens and per-user data must never land in Next's fetch cache.
      cache: 'no-store',
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (cause) {
    // 504: this app IS the proxy, and its upstream did not answer -- which is exactly what a
    // Gateway Timeout means. Raised as an ApiError so a caller that catches ApiError catches
    // this too, rather than a bare DOMException leaking through.
    if (cause instanceof DOMException && cause.name === 'TimeoutError') {
      throw new ApiError(
        504,
        `Timed out after ${REQUEST_TIMEOUT_MS}ms calling ${path}`,
        'EDGE_TIMEOUT'
      );
    }
    throw cause;
  }

  // Every response from the edge is JSON, including errors (Envoy's convert_grpc_status turns the
  // gRPC status into a google.rpc.Status body). A gateway-level failure might not be, so guard.
  const payload = await response.json().catch(() => undefined);

  if (!response.ok) {
    throw toApiError(response.status, payload);
  }
  return payload;
}

// --- the app's own token ---------------------------------------------------------------------

/**
 * OAuth 2.0 client credentials (RFC 6749 §4.4). Identifies THIS APP, not a person, which is what
 * lets the server read public data for a visitor who has not logged in.
 *
 * Cached in a module-level variable, safe precisely because it is not per-user: every request
 * would otherwise fetch an identical token. A USER token must never be cached this way on a
 * server. Mirrors ServiceTokenSupplier on the Java side, down to the refresh margin.
 */
const REFRESH_MARGIN_MS = 30_000;

type CachedToken = { accessToken: string; expiresAtMs: number };

let cached: CachedToken | undefined;
let inFlight: Promise<CachedToken> | undefined;

async function getClientToken(): Promise<string> {
  if (cached && Date.now() < cached.expiresAtMs - REFRESH_MARGIN_MS) {
    return cached.accessToken;
  }

  // Concurrent renders share one request instead of stampeding the token endpoint.
  inFlight ??= requestClientToken().finally(() => {
    inFlight = undefined;
  });

  cached = await inFlight;
  return cached.accessToken;
}

async function requestClientToken(): Promise<CachedToken> {
  const env = serverEnv();
  const basic = Buffer.from(
    `${env.PUBLICWEB_CLIENT_ID}:${env.PUBLICWEB_CLIENT_SECRET}`
  ).toString('base64');

  // Credentials go in the Authorization header, the form RFC 6749 §2.3.1 prefers over a body.
  // Calls `request` directly rather than `callApi`: this IS how a client token is obtained, so
  // asking for one here would be circular.
  const payload = await request('/api/token/client', {
    method: 'POST',
    body: {},
    headers: { Authorization: `Basic ${basic}` },
  });

  // Parsed by the generated schema rather than trusted as-is: this is proto3 canonical JSON, so
  // expires_in_seconds arrives as a STRING and becomes a bigint. ignoreUnknownFields keeps an
  // added backend field from breaking a frontend that hasn't regenerated yet.
  const token = fromJson(OAuth2TokenSchema, payload as JsonValue, {
    ignoreUnknownFields: true,
  });

  logger.debug('Fetched client token', {
    expiresInSeconds: String(token.expiresInSeconds),
  });

  return {
    accessToken: token.accessToken,
    expiresAtMs: Date.now() + Number(token.expiresInSeconds) * 1000,
  };
}

/** Test seam only: module-level state would otherwise leak between test cases. */
export function resetClientTokenCacheForTests(): void {
  cached = undefined;
  inFlight = undefined;
}
