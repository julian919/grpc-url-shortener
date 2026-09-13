import 'server-only';

import { cookies } from 'next/headers';

import type {
  LoginResponse,
  OAuth2Token,
} from '@/shared/api/gen/auth/api/auth_api_pb';

/**
 * The logged-in user's tokens, kept in two cookies. This file is the ONLY place that knows the
 * cookie names and options -- everything else calls these three functions.
 *
 *   login action          -> setSession(loginResponse)
 *   create-link action    -> readSession() -> callApi(..., { auth: { accessToken } })
 *   access token expired  -> refresh -> setSession(newTokens)
 *   refresh token dead    -> clearSession() -> redirect('/login')
 *   logout                -> clearSession()
 *
 * Where each may be called is a Next.js rule, not style: setSession and clearSession only in a
 * Server Action or Route Handler (cookies cannot be set while a Server Component renders -- the
 * response headers are already on their way). readSession works in any server code; inside a
 * Server Component it must sit behind <Suspense>, because cookies are request data.
 */

const ACCESS_COOKIE = 'access_token';
const REFRESH_COOKIE = 'refresh_token';

/**
 * Must match auth-service's `app.jwt.refresh-token-ttl` (168h). The login response only reports
 * the ACCESS token's lifetime, so this one is a constant. Getting it wrong is not cosmetic: if the
 * refresh cookie reused the access token's 15 minutes, the browser would delete the refresh token
 * with it and every user would be logged out after 15 minutes.
 */
const REFRESH_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

function cookieOptions(maxAge: number) {
  return {
    // JavaScript in the browser cannot read it, so an injected script cannot steal the token.
    httpOnly: true,
    // HTTPS only in production. Off in development, where localhost is plain HTTP.
    secure: process.env.NODE_ENV === 'production',
    // Not sent on cross-site form posts (blocks most CSRF), still sent when following a link here.
    sameSite: 'lax',
    path: '/',
    maxAge,
  } as const;
}

/** Store freshly issued tokens: a login response, or the token from a refresh. */
export async function setSession(
  token: LoginResponse | OAuth2Token
): Promise<void> {
  const cookiesStore = await cookies();
  cookiesStore.set(
    ACCESS_COOKIE,
    token.accessToken,
    cookieOptions(Number(token.expiresInSeconds))
  );
  cookiesStore.set(
    REFRESH_COOKIE,
    token.refreshToken,
    cookieOptions(REFRESH_MAX_AGE_SECONDS)
  );
}

/**
 * The current session, or null when there is no refresh token -- i.e. not logged in.
 *
 * `accessToken` can be undefined while the session is still valid: its cookie expires after 15
 * minutes, but the refresh token lives on and can get a new one. The return type is written out
 * here rather than generated because cookies hold two plain strings, not a proto message.
 */
export async function readSession(): Promise<{
  accessToken: string | undefined;
  refreshToken: string;
} | null> {
  const store = await cookies();
  const refreshToken = store.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) return null;

  return { accessToken: store.get(ACCESS_COOKIE)?.value, refreshToken };
}

/** Forget the user. Call on logout, or when the refresh token has been rejected. */
export async function clearSession(): Promise<void> {
  const store = await cookies();
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}
