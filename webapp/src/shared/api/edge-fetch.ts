import 'server-only';

import { serverEnv } from '@/shared/config/env';
import { toApiError } from '@/shared/api/errors';

/**
 * The one place that talks to the Envoy edge. Deliberately auth-agnostic: callers pass whatever
 * Authorization header they need, which keeps this module free of any dependency on token
 * handling (client-token.ts builds on it, so the reverse would be an import cycle).
 *
 * Returns the parsed JSON body as `unknown` on purpose -- the caller narrows it with the
 * generated proto schema (`fromJson`), which is the only thing that should decide shape.
 */
export async function edgeFetch(
  path: string,
  options: {
    method?: 'GET' | 'POST';
    body?: unknown;
    headers?: Record<string, string>;
  } = {}
): Promise<unknown> {
  const { method = 'GET', body, headers = {} } = options;
  const url = `${serverEnv().API_BASE_URL}${path}`;

  const response = await fetch(url, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    // Tokens and per-user data must never land in Next's fetch cache.
    cache: 'no-store',
  });

  // Every response from the edge is JSON, including errors (Envoy's convert_grpc_status turns the
  // gRPC status into a google.rpc.Status body). A gateway-level failure might not be, so guard.
  const payload = await response.json().catch(() => undefined);

  if (!response.ok) {
    throw toApiError(response.status, payload);
  }

  return payload;
}
