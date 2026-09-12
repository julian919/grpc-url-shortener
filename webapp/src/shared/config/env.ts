import 'server-only';

import { z } from 'zod';

// Everything the server needs from the environment, validated in ONE place. Per Next's data
// security guide, the data access layer should be the only code touching process.env -- nothing
// else in this app reads it.
//
// Note there is no NEXT_PUBLIC_* var here, and there must never be: that prefix inlines a value
// into the browser bundle, and PUBLICWEB_CLIENT_SECRET is a real credential.
const envSchema = z.object({
  /** The Envoy edge in front of every gRPC service. */
  API_BASE_URL: z.url().default('http://localhost:8080'),
  /** The client this app authenticates as for public reads (permission: LIST_SHORT_URL). */
  PUBLICWEB_CLIENT_ID: z.string().min(1).default('publicweb'),
  PUBLICWEB_CLIENT_SECRET: z
    .string()
    .min(1, 'PUBLICWEB_CLIENT_SECRET is required -- copy .env.example to .env.local'),
});

export type ServerEnv = z.infer<typeof envSchema>;

let cached: ServerEnv | undefined;

/**
 * Parsed lazily, not at module load, so that `next build` -- which imports this module while
 * compiling routes -- doesn't require a secret to be present just to produce a bundle. The first
 * request that actually needs the environment fails loudly instead.
 */
export function serverEnv(): ServerEnv {
  cached ??= envSchema.parse(process.env);
  return cached;
}
