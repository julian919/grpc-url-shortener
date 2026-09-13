import type { Route } from 'next';
import { redirect } from 'next/navigation';

import { parsePagination } from '@/features/links/schema/pagination';
import { listLinks } from '@/features/links/server/link-repository';
import { Pagination } from '@/features/links/components/pagination';
import {
  LinkStatus,
  type ShortLink,
} from '@/shared/api/gen/shortener/api/shortener_api_pb';

type SearchParams = Record<string, string | string[] | undefined>;

/**
 * An async Server Component: it runs only on the server, so the client token it triggers never
 * reaches the browser. It reads searchParams, which is request data, so the page keeps it behind
 * a <Suspense> boundary -- with Cache Components that placement is what lets the rest of the page
 * prerender into the static shell.
 */
export async function LinkList({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const pagination = parsePagination(await searchParams);
  const { shortLinks, pageInfo } = await listLinks(pagination);

  // A page past the end of the data (a hand-edited URL, or links deleted since) would otherwise
  // render an empty list that looks like "no links exist". Go to the last page that has links.
  const lastPage = pageInfo?.totalPages ?? 0;
  if (lastPage > 0 && pagination.page > lastPage) {
    redirect(`/?page=${lastPage}&pageSize=${pagination.pageSize}` as Route);
  }

  if (shortLinks.length === 0) {
    return (
      <p className="py-8 text-sm text-gray-600 dark:text-gray-400">
        No links yet. Creating one needs a signed-in user — that&apos;s exercise
        C in <code className="font-mono">docs/PRACTICE.md</code>.
      </p>
    );
  }

  return (
    <>
      <ul className="divide-y divide-gray-200 dark:divide-gray-800">
        {shortLinks.map((link) => (
          <LinkRow key={link.shortCode} link={link} />
        ))}
      </ul>
      {/* page_info is a message field, so proto3 makes it optional however reliably the server
          sends it. Defaulted here, at the point of use, rather than in the repository. */}
      <Pagination
        page={pageInfo?.page ?? pagination.page}
        pageSize={pageInfo?.pageSize ?? pagination.pageSize}
        totalPages={pageInfo?.totalPages ?? 1}
        totalCount={pageInfo?.totalCount ?? shortLinks.length}
      />
    </>
  );
}

function LinkRow({ link }: { link: ShortLink }) {
  return (
    <li className="py-4">
      <div className="flex items-center gap-2">
        <span className="font-mono text-sm font-medium">{link.shortCode}</span>
        <StatusBadge status={link.status} />
      </div>
      <p className="mt-1 truncate text-sm text-gray-600 dark:text-gray-400">
        {link.longUrl}
      </p>
      <p className="mt-1 text-xs text-gray-500">
        {/* createdAt is int64 -> bigint (epoch millis). ISO rather than a locale format, so the
            output doesn't depend on where it rendered. */}
        created{' '}
        {new Date(Number(link.createdAt))
          .toISOString()
          .replace('T', ' ')
          .slice(0, 16)}
      </p>
    </li>
  );
}

function StatusBadge({ status }: { status: LinkStatus }) {
  // Numeric enums carry a reverse mapping, so this is the generated name, e.g. "ACTIVE".
  const label = LinkStatus[status] ?? 'UNKNOWN';
  const tone =
    status === LinkStatus.ACTIVE
      ? 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
      : 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300';

  return (
    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${tone}`}>
      {label.toLowerCase()}
    </span>
  );
}
