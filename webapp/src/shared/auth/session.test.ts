import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { create } from '@bufbuild/protobuf';

import {
  LoginResponseSchema,
  OAuth2TokenSchema,
} from '@/shared/api/gen/auth/api/auth_api_pb';
import { clearSession, readSession, setSession } from '@/shared/auth/session';

// A fake cookie store standing in for Next's `cookies()`. vi.hoisted because vi.mock is hoisted
// above ordinary declarations, so the factory could not otherwise see `store`.
const store = vi.hoisted(() => ({
  jar: new Map<string, string>(),
  set: vi.fn(),
  get: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('next/headers', () => ({ cookies: vi.fn(async () => store) }));

const loginResponse = create(LoginResponseSchema, {
  accessToken: 'acc-1',
  expiresInSeconds: 900n,
  refreshToken: 'ref-1',
});

describe('session', () => {
  beforeEach(() => {
    store.jar.clear();
    store.set
      .mockReset()
      .mockImplementation((name: string, value: string) =>
        store.jar.set(name, value)
      );
    store.get
      .mockReset()
      .mockImplementation((name: string) =>
        store.jar.has(name) ? { name, value: store.jar.get(name) } : undefined
      );
    store.delete
      .mockReset()
      .mockImplementation((name: string) => store.jar.delete(name));
  });

  afterEach(() => vi.unstubAllEnvs());

  describe('setSession', () => {
    it('stores the access token as an HttpOnly, lax, site-wide cookie that lives as long as the token', async () => {
      await setSession(loginResponse);

      expect(store.set).toHaveBeenCalledWith('access_token', 'acc-1', {
        httpOnly: true,
        secure: false,
        sameSite: 'lax',
        path: '/',
        maxAge: 900,
      });
    });

    it("gives the refresh cookie 7 days, NOT the access token's 15 minutes", async () => {
      // If this reused 900, the browser would delete the refresh token after 15 minutes and log
      // every user out.
      await setSession(loginResponse);

      expect(store.set).toHaveBeenCalledWith(
        'refresh_token',
        'ref-1',
        expect.objectContaining({ httpOnly: true, maxAge: 7 * 24 * 60 * 60 })
      );
    });

    it('marks cookies secure in production only', async () => {
      vi.stubEnv('NODE_ENV', 'production');

      await setSession(loginResponse);

      expect(store.set).toHaveBeenCalledWith(
        'access_token',
        'acc-1',
        expect.objectContaining({ secure: true })
      );
    });

    it('accepts the token from a refresh response too, not just a login', async () => {
      const refreshed = create(OAuth2TokenSchema, {
        accessToken: 'acc-2',
        tokenType: 'bearer',
        expiresInSeconds: 900n,
        refreshToken: 'ref-2',
      });

      await setSession(refreshed);

      await expect(readSession()).resolves.toEqual({
        accessToken: 'acc-2',
        refreshToken: 'ref-2',
      });
    });
  });

  describe('readSession', () => {
    it('returns both tokens after login', async () => {
      await setSession(loginResponse);

      await expect(readSession()).resolves.toEqual({
        accessToken: 'acc-1',
        refreshToken: 'ref-1',
      });
    });

    it('is still a session when only the access cookie has expired -- the refresh token can renew it', async () => {
      store.jar.set('refresh_token', 'ref-1');

      await expect(readSession()).resolves.toEqual({
        accessToken: undefined,
        refreshToken: 'ref-1',
      });
    });

    it('returns null when there is no refresh token', async () => {
      store.jar.set('access_token', 'acc-1');

      await expect(readSession()).resolves.toBeNull();
    });
  });

  describe('clearSession', () => {
    it('deletes both cookies', async () => {
      await setSession(loginResponse);

      await clearSession();

      expect(store.delete).toHaveBeenCalledWith('access_token');
      expect(store.delete).toHaveBeenCalledWith('refresh_token');
      await expect(readSession()).resolves.toBeNull();
    });
  });
});
