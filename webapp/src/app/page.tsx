import { Suspense } from 'react';

import { LinkList } from '@/features/links/components/link-list';
import { LinkListSkeleton } from '@/features/links/components/link-list-skeleton';

type SearchParams = Record<string, string | string[] | undefined>;

/**
 * Routing glue only: this file assembles the feature and owns the Suspense boundary. Everything
 * outside the boundary is static and prerenders into the shell; the list reads request data
 * (searchParams, then the API) so it streams in behind it.
 *
 * Note that searchParams is passed down as a PROMISE and awaited inside the boundary. Awaiting it
 * here would drag the whole page out of the static shell.
 */
export default function Home({ searchParams }: { searchParams: Promise<SearchParams> }) {
  return (
    <main className="mx-auto max-w-2xl p-8">
      <h1 className="text-2xl font-semibold">Short links</h1>
      <p className="mt-1 mb-6 text-sm text-gray-600 dark:text-gray-400">
        Public list, read with this app&apos;s own client token — no sign-in required.
      </p>

      <Suspense fallback={<LinkListSkeleton />}>
        <LinkList searchParams={searchParams} />
      </Suspense>
    </main>
  );
}
