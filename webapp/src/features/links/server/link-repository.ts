import 'server-only';

import { fromJson, type JsonValue } from '@bufbuild/protobuf';

import { callApi } from '@/shared/api/client';
import {
  ListShortLinksResponseSchema,
  type ListShortLinksResponse,
} from '@/shared/api/gen/shortener/api/shortener_api_pb';

import type { Pagination } from '@/features/links/schema/pagination';

/**
 * The data access layer for links: the only module that knows how to reach the shortener service.
 * `server-only` means a client component importing this is a build error, which is what keeps the
 * client token on the server.
 *
 * Returns the GENERATED type as-is. No hand-written view model: a local interface mirroring the
 * response would be one more thing to update when the proto changes, which is the drift codegen
 * exists to prevent. Callers deal with proto3 optionality themselves -- `pageInfo` is a message
 * field, so it is `PageInfo | undefined` no matter how certain the server is to send it. Same
 * shape as Cognixus's `repositories/<domain>_repository.ts`, which declare no local types at all.
 */
export async function listLinks({
  page,
  pageSize,
}: Pagination): Promise<ListShortLinksResponse> {
  // ListShortLinks requires LIST_SHORT_URL, which `publicweb` holds -- so a signed-out
  // visitor can see this list, while creating a link still needs a user token.
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const payload = await callApi(`/api/links?${query}`, { auth: 'client' });

  // fromJson, not a cast: this is proto3 canonical JSON, where int64 arrives as a string
  // (createdAt) and enums arrive by name ("LINK_STATUS_ACTIVE").
  return fromJson(ListShortLinksResponseSchema, payload as JsonValue, {
    ignoreUnknownFields: true,
  });
}
