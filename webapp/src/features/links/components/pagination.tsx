import Link from 'next/link';

// Object hrefs rather than template strings: with typedRoutes on, this is what lets TypeScript
// check the destination while still carrying query params.
function pageHref(page: number, pageSize: number) {
  return { pathname: '/', query: { page: String(page), pageSize: String(pageSize) } } as const;
}

export function Pagination({
  page,
  pageSize,
  totalPages,
}: {
  page: number;
  pageSize: number;
  totalPages: number;
}) {
  if (totalPages <= 1) return null;

  const previous = page > 1 ? page - 1 : null;
  const next = page < totalPages ? page + 1 : null;

  return (
    <nav className="mt-6 flex items-center gap-4 text-sm" aria-label="Pagination">
      {previous === null ? (
        <span className="text-gray-400">Previous</span>
      ) : (
        <Link className="underline" href={pageHref(previous, pageSize)}>
          Previous
        </Link>
      )}

      <span className="text-gray-600 dark:text-gray-400">
        Page {page} of {totalPages}
      </span>

      {next === null ? (
        <span className="text-gray-400">Next</span>
      ) : (
        <Link className="underline" href={pageHref(next, pageSize)}>
          Next
        </Link>
      )}
    </nav>
  );
}
