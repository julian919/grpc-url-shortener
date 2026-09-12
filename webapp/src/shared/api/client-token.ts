import 'server-only';

import { fromJson, type JsonValue } from '@bufbuild/protobuf';

import { edgeFetch } from '@/shared/api/edge-fetch';
import { OAuth2TokenSchema } from '@/shared/api/gen/auth/api/auth_api_pb';
import { serverEnv } from '@/shared/config/env';
import { logger } from '@/shared/observability/logger';

/**
 * The application's own credential (OAuth 2.0 client credentials, RFC 6749 §4.4). It identifies
 * THIS APP, not a person, and it is what lets the server read public data -- `publicweb` holds
 * the LIST_SHORT_URL permission -- for a visitor who has not logged in.
 *
 * Cached in a module-level variable, which is safe precisely because it is not per-user: every
 * request would fetch an identical token. A USER token must never be cached this way on a server.
 * This mirrors user-service's ServiceTokenSupplier on the Java side, down to the refresh margin.
 */
const REFRESH_MARGIN_MS = 30_000;

type CachedToken = { accessToken: string; expiresAtMs: number };

let cached: CachedToken | undefined;
let inFlight: Promise<CachedToken> | undefined;

export async function getClientToken(): Promise<string> {
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
  const payload = await edgeFetch('/api/token/client', {
    method: 'POST',
    body: {},
    headers: { Authorization: `Basic ${basic}` },
  });

  // Parsed by the generated schema rather than trusted as-is: this is proto3 canonical JSON, so
  // expires_in_seconds arrives as a STRING and becomes a bigint. ignoreUnknownFields keeps an
  // added backend field from breaking a frontend that hasn't regenerated yet.
  const token = fromJson(OAuth2TokenSchema, payload as JsonValue, { ignoreUnknownFields: true });

  logger.debug('Fetched client token', { expiresInSeconds: String(token.expiresInSeconds) });

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
