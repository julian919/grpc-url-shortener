import { z } from 'zod';

/**
 * URL search params are untrusted input, so they get parsed at the boundary like any other.
 * `.catch()` keeps a hand-edited URL (`?page=banana`) from throwing: it falls back to the default
 * rather than rendering an error page.
 *
 * Mirrors the backend's result window (LinkService.MAX_RESULT_WINDOW): only the first 10,000 rows
 * can be paged through. It is a limit on ROWS, so how many pages that allows depends on pageSize.
 * A URL past it is held at the last reachable page -- the same place the disabled Next button
 * stops -- rather than reaching the server and earning an INVALID_ARGUMENT error page.
 */
export const MAX_RESULT_WINDOW = 10_000;

export const paginationSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1),
  pageSize: z.coerce.number().int().min(1).max(100).catch(10),
});

export type Pagination = z.infer<typeof paginationSchema>;

type RawSearchParams = Record<string, string | string[] | undefined>;

export function parsePagination(
  searchParams: RawSearchParams = {}
): Pagination {
  const first = (value: string | string[] | undefined) =>
    Array.isArray(value) ? value[0] : value;

  const parsed = paginationSchema.parse({
    page: first(searchParams.page) ?? 1,
    pageSize: first(searchParams.pageSize) ?? 10,
  });

  // Checked after parsing because the limit depends on both values together.
  const lastReachablePage = Math.floor(MAX_RESULT_WINDOW / parsed.pageSize);
  return { ...parsed, page: Math.min(parsed.page, lastReachablePage) };
}
