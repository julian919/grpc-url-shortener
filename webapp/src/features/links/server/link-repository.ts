import 'server-only';

import { create, fromJson, type JsonValue } from '@bufbuild/protobuf';

import { callApi } from '@/shared/api/http';
import {
  PageInfoSchema,
  ListShortLinksResponseSchema,
  type PageInfo,
  type ShortLink,
} from '@/shared/api/gen/shortener/api/shortener_api_pb';

import type { Pagination } from '@/features/links/schema/pagination';

/**
 * The data access layer for links: the only module that knows how to reach the shortener service.
 * `server-only` means a client component importing this is a build error, which is what keeps the
 * client token on the server.
 *
 * Types are not declared here -- they come from the generated proto schema, so there is nothing to
 * drift. Cognixus's `repositories/<domain>_repository.ts` files work the same way.
 */
export type LinkPage = {
  // This app's own view model, so `links` stays short here. On the wire the field is
  // short_links -- named for the resource type, per AIP-122 -- hence the mapping below.
  links: ShortLink[];
  pageInfo: PageInfo;
};

export async function listLinks({ page, pageSize }: Pagination): Promise<LinkPage> {
  // ListShortLinks requires LIST_SHORT_URL, which `publicweb` holds -- so a signed-out
  // visitor can see this list, while creating a link still needs a user token.
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const payload = await callApi(`/api/links?${query}`, { auth: 'client' });

  // fromJson, not a cast: this is proto3 canonical JSON, where int64 arrives as a string
  // (createdAt) and enums arrive by name ("LINK_STATUS_ACTIVE").
  const response = fromJson(ListShortLinksResponseSchema, payload as JsonValue, {
    ignoreUnknownFields: true,
  });

  return {
    links: response.shortLinks,
    // page_info is a message field, so it is optional on the wire; fall back rather than crash.
    pageInfo:
      response.pageInfo ??
      create(PageInfoSchema, {
        page,
        pageSize,
        totalCount: response.shortLinks.length,
        totalPages: 1,
      }),
  };
}
