import { describe, expect, it } from 'vitest';

import { ApiError, toApiError } from '@/shared/api/errors';

// The bodies below are copied verbatim from real responses of the running edge, so this test
// fails if the backend's error shape ever changes.
describe('toApiError', () => {
  it('pulls reason out of a google.rpc.ErrorInfo detail', () => {
    const body = {
      code: 16,
      message: 'Access token expired',
      details: [
        {
          '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
          reason: 'ACCESS_TOKEN_EXPIRED',
          domain: 'auth.example.com',
          metadata: {},
        },
      ],
    };

    const error = toApiError(401, body);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.httpStatus).toBe(401);
    expect(error.grpcCode).toBe(16);
    expect(error.message).toBe('Access token expired');
    expect(error.reason).toBe('ACCESS_TOKEN_EXPIRED');
    expect(error.isExpiredAccessToken).toBe(true);
    expect(error.isDeadRefreshToken).toBe(false);
  });

  it('flags a dead refresh token, which is a 400 and not a 401', () => {
    const error = toApiError(400, {
      code: 3,
      message: 'Refresh token expired',
      details: [
        {
          '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
          reason: 'REFRESH_TOKEN_EXPIRED',
          domain: 'auth.example.com',
        },
      ],
    });

    expect(error.isDeadRefreshToken).toBe(true);
    expect(error.isExpiredAccessToken).toBe(false);
  });

  it('survives an error with no details, e.g. a permission failure', () => {
    const error = toApiError(403, { code: 7, message: 'Access denied', details: [] });

    expect(error.message).toBe('Access denied');
    expect(error.reason).toBeUndefined();
  });

  it('survives a non-JSON / proxy-generated failure', () => {
    const error = toApiError(502, undefined);

    expect(error.httpStatus).toBe(502);
    expect(error.message).toContain('502');
    expect(error.reason).toBeUndefined();
  });

  it('ignores details that are not ErrorInfo', () => {
    const error = toApiError(400, {
      code: 3,
      message: 'Bad request',
      details: [{ '@type': 'type.googleapis.com/google.rpc.BadRequest', fieldViolations: [] }],
    });

    expect(error.reason).toBeUndefined();
  });

  it('degrades to a plain HTTP error when the body is not a google.rpc.Status', async () => {
    // A gateway-generated 502 is an HTML page, not JSON -- edgeFetch passes `undefined` here.
    const error = toApiError(502, undefined);

    expect(error.httpStatus).toBe(502);
    expect(error.message).toBe('Request failed with HTTP 502');
    expect(error.reason).toBeUndefined();
    expect(error.grpcCode).toBeUndefined();
    expect(error.isExpiredAccessToken).toBe(false);
    expect(error.isDeadRefreshToken).toBe(false);
  });
});
