import { describe, expect, it } from 'vitest';

import { parsePagination } from '@/features/links/schema/pagination';

describe('parsePagination', () => {
  it('defaults when no params are present', () => {
    expect(parsePagination()).toEqual({ page: 1, pageSize: 20 });
  });

  it('coerces the string values a URL actually carries', () => {
    expect(parsePagination({ page: '3', pageSize: '50' })).toEqual({ page: 3, pageSize: 50 });
  });

  it('falls back instead of throwing on junk, so a hand-edited URL cannot error the page', () => {
    expect(parsePagination({ page: 'banana', pageSize: '-5' })).toEqual({ page: 1, pageSize: 20 });
  });

  it('caps pageSize so a caller cannot request the whole table', () => {
    expect(parsePagination({ pageSize: '5000' }).pageSize).toBe(20);
  });

  it('takes the first value when a param is repeated', () => {
    expect(parsePagination({ page: ['2', '9'] }).page).toBe(2);
  });
});
