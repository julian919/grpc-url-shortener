'use client';

// Segment error boundary. The usual reason this renders in development is that the backend isn't
// running: `docker compose up -d` from code/, then hit Try again.
export default function Error({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="mx-auto max-w-2xl p-8">
      <h1 className="text-xl font-semibold">Could not load links</h1>
      <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
        The API did not respond. If you are running locally, check that the backend is up
        (<code className="font-mono">docker compose up -d</code>) and that{' '}
        <code className="font-mono">.env.local</code> exists.
      </p>
      <button
        type="button"
        onClick={reset}
        className="mt-4 rounded border px-3 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-900"
      >
        Try again
      </button>
    </main>
  );
}
