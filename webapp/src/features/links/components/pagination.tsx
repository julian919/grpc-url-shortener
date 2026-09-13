import Link from 'next/link';

import { MAX_RESULT_WINDOW } from '@/features/links/schema/pagination';

export function Pagination({
  page,
  pageSize,
  totalPages,
  totalCount,
}: {
  page: number;
  pageSize: number;
  totalPages: number;
  totalCount: number;
}) {
  const href = (p: number) =>
    ({
      pathname: '/',
      query: { page: String(p), pageSize: String(pageSize) },
    }) as const;

  // The backend stops counting at 10,001, so anything above the window means "more than 10,000".
  const total =
    totalCount > MAX_RESULT_WINDOW
      ? `${MAX_RESULT_WINDOW.toLocaleString('en-US')}+`
      : totalCount.toLocaleString('en-US');

  // Pages stop at the 10,000-row window. If they can't hold every link, say why Next is disabled.
  const reachable = totalPages * pageSize;

  return (
    <div className="mt-6 space-y-2 text-sm">
      <nav className="flex items-center gap-4" aria-label="Pagination">
        {page > 1 ? (
          <Link className="underline" href={href(page - 1)}>
            Previous
          </Link>
        ) : (
          <span className="text-gray-400">Previous</span>
        )}

        <span className="text-gray-600 dark:text-gray-400">
          Page {page} of {Math.max(totalPages, 1)} · {total} links
        </span>

        {page < totalPages ? (
          <Link className="underline" href={href(page + 1)}>
            Next
          </Link>
        ) : (
          <span className="text-gray-400">Next</span>
        )}
      </nav>

      {totalPages > 0 && totalCount > reachable && (
        <p className="text-xs text-amber-700 dark:text-amber-400">
          Only the first {reachable.toLocaleString('en-US')} links can be
          browsed page by page.
        </p>
      )}
    </div>
  );
}
