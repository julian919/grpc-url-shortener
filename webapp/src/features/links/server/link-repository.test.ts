import { beforeEach, describe, expect, it, vi } from 'vitest';

import { callApi } from '@/shared/api/http';
import { listLinks } from '@/features/links/server/link-repository';
import { LinkStatus } from '@/shared/api/gen/shortener/api/shortener_api_pb';

vi.mock('@/shared/api/http', () => ({ callApi: vi.fn() }));

const mockedCallApi = vi.mocked(callApi);

// Copied from a real response of the running edge. Note what proto3 JSON does: createdAt is a
// STRING (int64) and status is the enum's NAME. If this test passes, the parse matches the wire.
const edgeResponse = {
  shortLinks: [
    {
      shortCode: 'vrwRhj_bSO6MGnZigJyc-w',
      longUrl: 'https://example.com/three-services',
      createdAt: '1789049822223',
      status: 'LINK_STATUS_ACTIVE',
    },
  ],
  pageInfo: { page: 1, pageSize: 20, totalCount: 1, totalPages: 1 },
};

describe('listLinks', () => {
  beforeEach(() => mockedCallApi.mockReset());

  it('calls the edge with the client token and the paging query', async () => {
    mockedCallApi.mockResolvedValue(edgeResponse);

    await listLinks({ page: 2, pageSize: 50 });

    expect(mockedCallApi).toHaveBeenCalledWith('/api/links?page=2&pageSize=50', {
      auth: 'client',
    });
  });

  it('parses proto3 JSON into generated types', async () => {
    mockedCallApi.mockResolvedValue(edgeResponse);

    const { links, pageInfo } = await listLinks({ page: 1, pageSize: 20 });

    expect(links).toHaveLength(1);
    expect(links[0]?.shortCode).toBe('vrwRhj_bSO6MGnZigJyc-w');
    // int64 -> bigint, enum name -> enum member.
    expect(links[0]?.createdAt).toBe(1789049822223n);
    expect(links[0]?.status).toBe(LinkStatus.ACTIVE);
    expect(pageInfo.totalPages).toBe(1);
  });

  it('tolerates an absent page_info, which is optional on the wire', async () => {
    mockedCallApi.mockResolvedValue({ shortLinks: [] });

    const { links, pageInfo } = await listLinks({ page: 1, pageSize: 20 });

    expect(links).toEqual([]);
    expect(pageInfo).toMatchObject({ page: 1, pageSize: 20, totalCount: 0, totalPages: 1 });
  });

  it('ignores fields the backend added but this client has not regenerated', async () => {
    mockedCallApi.mockResolvedValue({ ...edgeResponse, somethingNew: 'ignored' });

    await expect(listLinks({ page: 1, pageSize: 20 })).resolves.toBeDefined();
  });
});
