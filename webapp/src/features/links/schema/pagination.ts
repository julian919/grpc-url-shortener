import { z } from 'zod';

/**
 * URL search params are untrusted input, so they get parsed at the boundary like any other.
 * `.catch()` keeps a hand-edited URL (`?page=banana`) from throwing: it falls back to the
 * default rather than rendering an error page.
 */
export const paginationSchema = z.object({
  page: z.coerce.number().int().min(1).max(10_000).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(20),
});

export type Pagination = z.infer<typeof paginationSchema>;

type RawSearchParams = Record<string, string | string[] | undefined>;

export function parsePagination(searchParams: RawSearchParams = {}): Pagination {
  const first = (value: string | string[] | undefined) =>
    Array.isArray(value) ? value[0] : value;

  return paginationSchema.parse({
    page: first(searchParams.page) ?? 1,
    pageSize: first(searchParams.pageSize) ?? 20,
  });
}
