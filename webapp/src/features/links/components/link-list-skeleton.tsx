// Shown while the list streams in. It sits in the static shell, so it is what a visitor sees
// instantly, before the request-time read behind <Suspense> resolves.
export function LinkListSkeleton() {
  return (
    <ul className="divide-y divide-gray-200 dark:divide-gray-800" aria-busy="true">
      {Array.from({ length: 5 }, (_, index) => (
        <li key={index} className="animate-pulse py-4">
          <div className="h-4 w-40 rounded bg-gray-200 dark:bg-gray-800" />
          <div className="mt-2 h-3 w-72 rounded bg-gray-100 dark:bg-gray-900" />
        </li>
      ))}
    </ul>
  );
}
