import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { edgeFetch } from '@/shared/api/edge-fetch';
import { getClientToken, resetClientTokenCacheForTests } from '@/shared/api/client-token';

vi.mock('@/shared/api/edge-fetch', () => ({ edgeFetch: vi.fn() }));

const mockedEdgeFetch = vi.mocked(edgeFetch);

// proto3 canonical JSON: int64 comes back as a STRING, which is exactly what the edge sends.
const tokenResponse = (accessToken: string, expiresInSeconds = '300') => ({
  accessToken,
  tokenType: 'bearer',
  expiresInSeconds,
});

describe('getClientToken', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubEnv('PUBLICWEB_CLIENT_ID', 'publicweb');
    vi.stubEnv('PUBLICWEB_CLIENT_SECRET', 's3cret');
    resetClientTokenCacheForTests();
    mockedEdgeFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  it('sends the client credentials as Basic auth to the token endpoint', async () => {
    mockedEdgeFetch.mockResolvedValue(tokenResponse('tok-1'));

    await expect(getClientToken()).resolves.toBe('tok-1');

    expect(mockedEdgeFetch).toHaveBeenCalledWith('/api/token/client', {
      method: 'POST',
      body: {},
      headers: {
        Authorization: `Basic ${Buffer.from('publicweb:s3cret').toString('base64')}`,
      },
    });
  });

  it('reuses the cached token while it is still valid', async () => {
    mockedEdgeFetch.mockResolvedValue(tokenResponse('tok-1'));

    await getClientToken();
    vi.advanceTimersByTime(60_000);
    await expect(getClientToken()).resolves.toBe('tok-1');

    expect(mockedEdgeFetch).toHaveBeenCalledTimes(1);
  });

  it('refetches once the token is inside the refresh margin', async () => {
    mockedEdgeFetch
      .mockResolvedValueOnce(tokenResponse('tok-1'))
      .mockResolvedValueOnce(tokenResponse('tok-2'));

    await getClientToken();
    // 300s TTL, 30s margin: at 4m45s it is still good, just past 4m30s it is not.
    vi.advanceTimersByTime(271_000);

    await expect(getClientToken()).resolves.toBe('tok-2');
    expect(mockedEdgeFetch).toHaveBeenCalledTimes(2);
  });

  it('lets concurrent callers share one in-flight request', async () => {
    mockedEdgeFetch.mockResolvedValue(tokenResponse('tok-1'));

    const [first, second, third] = await Promise.all([
      getClientToken(),
      getClientToken(),
      getClientToken(),
    ]);

    expect([first, second, third]).toEqual(['tok-1', 'tok-1', 'tok-1']);
    expect(mockedEdgeFetch).toHaveBeenCalledTimes(1);
  });

  it('does not cache a failure, so the next call retries', async () => {
    mockedEdgeFetch
      .mockRejectedValueOnce(new Error('edge down'))
      .mockResolvedValueOnce(tokenResponse('tok-1'));

    await expect(getClientToken()).rejects.toThrow('edge down');
    await expect(getClientToken()).resolves.toBe('tok-1');
  });
});
