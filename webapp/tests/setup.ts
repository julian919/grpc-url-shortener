import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

// `server-only` throws by design when it ends up in a client bundle. Unit tests import those
// modules directly (that is the point -- they hold the token and secret handling), so stub it.
vi.mock('server-only', () => ({}));
