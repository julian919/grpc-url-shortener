import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Cache Components = stabilised PPR + the `use cache` directive. With it on, a page is a static
  // shell by default and anything reading request data (cookies, searchParams, uncached fetches)
  // must sit inside <Suspense> -- reading cookies() outside a boundary is a BUILD error, which is
  // exactly the discipline we want around session reads. See docs/ARCHITECTURE.md.
  cacheComponents: true,
  // Typed routes turn a broken <Link href> into a compile error.
  typedRoutes: true,
};

export default nextConfig;
