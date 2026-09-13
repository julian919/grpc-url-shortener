import { describe, expect, it } from 'vitest';

import { parsePagination } from '@/features/links/schema/pagination';

describe('parsePagination', () => {
  it('defaults when no params are present', () => {
    expect(parsePagination()).toEqual({ page: 1, pageSize: 10 });
  });

  it('coerces the string values a URL actually carries', () => {
    expect(parsePagination({ page: '3', pageSize: '50' })).toEqual({ page: 3, pageSize: 50 });
  });

  it('falls back instead of throwing on junk, so a hand-edited URL cannot error the page', () => {
    expect(parsePagination({ page: 'banana', pageSize: '-5' })).toEqual({ page: 1, pageSize: 10 });
  });

  it('holds a page past the 10,000-row window at the last reachable page, not page 1', () => {
    // The window is in rows, so the last reachable page depends on pageSize.
    expect(parsePagination({ page: '1000', pageSize: '10' }).page).toBe(1000);
    expect(parsePagination({ page: '1001', pageSize: '10' }).page).toBe(1000);
    expect(parsePagination({ page: '100', pageSize: '100' }).page).toBe(100);
    expect(parsePagination({ page: '101', pageSize: '100' }).page).toBe(100);
  });

  it('rejects an out-of-range pageSize back to the default rather than clamping', () => {
    expect(parsePagination({ pageSize: '5000' }).pageSize).toBe(10);
  });

  it('takes the first value when a param is repeated', () => {
    expect(parsePagination({ page: ['2', '9'] }).page).toBe(2);
  });
});
