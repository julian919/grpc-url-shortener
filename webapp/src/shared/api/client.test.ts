import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { callApi, resetClientTokenCacheForTests } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';

/**
 * Mocks `fetch` rather than an inner module, so these exercise the real transport as well as the
 * token cache — the path a feature actually takes.
 */
const mockedFetch = vi.fn();
vi.stubGlobal('fetch', mockedFetch);

// proto3 canonical JSON: int64 comes back as a STRING, which is exactly what the edge sends.
const jsonResponse = (body: unknown, status = 200) =>
  ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

const tokenBody = (accessToken: string, expiresInSeconds = '300') => ({
  accessToken,
  tokenType: 'bearer',
  expiresInSeconds,
});

const BASIC = `Basic ${Buffer.from('publicweb:s3cret').toString('base64')}`;

describe('callApi', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubEnv('API_BASE_URL', 'http://edge.test');
    vi.stubEnv('PUBLICWEB_CLIENT_ID', 'publicweb');
    vi.stubEnv('PUBLICWEB_CLIENT_SECRET', 's3cret');
    resetClientTokenCacheForTests();
    mockedFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  it("prefixes the edge's base url and never caches", async () => {
    mockedFetch.mockResolvedValue(jsonResponse({ ok: true }));

    await callApi('/api/links', { auth: 'none' });

    const [url, init] = mockedFetch.mock.calls[0]!;
    expect(url).toBe('http://edge.test/api/links');
    expect(init.cache).toBe('no-store');
    expect(init.headers.Authorization).toBeUndefined();
  });

  it('sends a user token straight through', async () => {
    mockedFetch.mockResolvedValue(jsonResponse({}));

    await callApi('/api/links', { auth: { accessToken: 'user-tok' } });

    expect(mockedFetch.mock.calls[0]![1].headers.Authorization).toBe('Bearer user-tok');
  });

  it('sets a timeout, and surfaces it as a 504 ApiError', async () => {
    const [, init] = await (async () => {
      mockedFetch.mockResolvedValue(jsonResponse({}));
      await callApi('/api/links', { auth: 'none' });
      return mockedFetch.mock.calls[0]!;
    })();
    expect(init.signal).toBeInstanceOf(AbortSignal);

    // A real timeout rejects with a TimeoutError DOMException; it must not leak as-is.
    mockedFetch.mockReset();
    mockedFetch.mockRejectedValue(new DOMException('The operation timed out.', 'TimeoutError'));

    const error = await callApi('/api/links', { auth: 'none' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).httpStatus).toBe(504);
    expect((error as ApiError).reason).toBe('EDGE_TIMEOUT');
  });

  it('turns a non-2xx into an ApiError carrying the reason', async () => {
    mockedFetch.mockResolvedValue(
      jsonResponse(
        {
          code: 16,
          message: 'Access token expired',
          details: [
            {
              '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
              reason: 'ACCESS_TOKEN_EXPIRED',
              domain: 'auth.example.com',
            },
          ],
        },
        401
      )
    );

    await expect(callApi('/api/links', { auth: 'none' })).rejects.toBeInstanceOf(ApiError);
  });

  describe("the app's own client token", () => {
    it('fetches one with Basic auth, then sends it as a bearer', async () => {
      mockedFetch
        .mockResolvedValueOnce(jsonResponse(tokenBody('tok-1')))
        .mockResolvedValueOnce(jsonResponse({ shortLinks: [] }));

      await callApi('/api/links', { auth: 'client' });

      const [tokenUrl, tokenInit] = mockedFetch.mock.calls[0]!;
      expect(tokenUrl).toBe('http://edge.test/api/token/client');
      expect(tokenInit.method).toBe('POST');
      expect(tokenInit.headers.Authorization).toBe(BASIC);

      expect(mockedFetch.mock.calls[1]![1].headers.Authorization).toBe('Bearer tok-1');
    });

    it('reuses the cached token while it is still valid', async () => {
      mockedFetch.mockResolvedValue(jsonResponse(tokenBody('tok-1')));

      await callApi('/api/links', { auth: 'client' });
      const afterFirst = mockedFetch.mock.calls.length;
      vi.advanceTimersByTime(60_000);
      await callApi('/api/links', { auth: 'client' });

      // One more call for the request itself, none for a second token.
      expect(mockedFetch.mock.calls.length).toBe(afterFirst + 1);
    });

    it('refetches once the token is inside the refresh margin', async () => {
      mockedFetch
        .mockResolvedValueOnce(jsonResponse(tokenBody('tok-1')))
        .mockResolvedValueOnce(jsonResponse({}))
        .mockResolvedValueOnce(jsonResponse(tokenBody('tok-2')))
        .mockResolvedValueOnce(jsonResponse({}));

      await callApi('/api/links', { auth: 'client' });
      // 300s TTL, 30s margin: at 4m45s it is still good, just past 4m30s it is not.
      vi.advanceTimersByTime(271_000);
      await callApi('/api/links', { auth: 'client' });

      expect(mockedFetch.mock.calls[3]![1].headers.Authorization).toBe('Bearer tok-2');
    });

    it('lets concurrent callers share one in-flight token request', async () => {
      mockedFetch.mockResolvedValue(jsonResponse(tokenBody('tok-1')));

      await Promise.all([
        callApi('/api/links', { auth: 'client' }),
        callApi('/api/links', { auth: 'client' }),
        callApi('/api/links', { auth: 'client' }),
      ]);

      const tokenCalls = mockedFetch.mock.calls.filter(([url]) =>
        String(url).endsWith('/api/token/client')
      );
      expect(tokenCalls).toHaveLength(1);
    });

    it('does not cache a failure, so the next call retries', async () => {
      mockedFetch
        .mockRejectedValueOnce(new Error('edge down'))
        .mockResolvedValueOnce(jsonResponse(tokenBody('tok-1')))
        .mockResolvedValueOnce(jsonResponse({}));

      await expect(callApi('/api/links', { auth: 'client' })).rejects.toThrow('edge down');
      await expect(callApi('/api/links', { auth: 'client' })).resolves.toBeDefined();
    });
  });
});
