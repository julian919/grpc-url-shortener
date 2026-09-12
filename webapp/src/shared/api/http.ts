import 'server-only';

import { edgeFetch } from '@/shared/api/edge-fetch';
import { getClientToken } from '@/shared/api/client-token';

/**
 * Which credential a call travels with. The same split Cognixus's session manager makes per call
 * with `TokenCallerType.Client` / `.User`:
 *
 * - `'none'`   — public endpoints (login, register, token endpoints)
 * - `'client'` — this app's own identity; used for public reads like the link list
 * - `{ accessToken }` — a signed-in person. Passed per call, never stashed in module state:
 *   on a server that state would be shared across everyone's requests.
 */
export type Auth = 'none' | 'client' | { accessToken: string };

export async function callApi(
  path: string,
  options: { method?: 'GET' | 'POST'; body?: unknown; auth: Auth }
): Promise<unknown> {
  const { method = 'GET', body, auth } = options;
  const headers: Record<string, string> = {};

  if (auth === 'client') {
    headers.Authorization = `Bearer ${await getClientToken()}`;
  } else if (typeof auth === 'object') {
    headers.Authorization = `Bearer ${auth.accessToken}`;
  }

  return edgeFetch(path, { method, body, headers });
}
