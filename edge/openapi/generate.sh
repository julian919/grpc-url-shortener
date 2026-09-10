#!/usr/bin/env bash
#
# Regenerates api.swagger.json from the proto contract.
#
# The edge exposes HTTP because each rpc carries an (google.api.http) option.
# The SAME options drive an OpenAPI 2.0 doc, via grpc-gateway's
# protoc-gen-openapiv2 -- so an importable API description falls out of the
# contract for free, no hand-written request collection.
#
# Import the output into Postman: Import -> File -> edge/openapi/api.swagger.json
# -> "Generate collection". Set the collection's base URL to
# http://localhost:8080 and add a collection-level Bearer token for the
# permission-gated endpoints.
#
# Rerun this after any change to a *_api.proto HTTP mapping. Everything runs in
# a throwaway container; no host Go/protoc needed.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$here/../.."
bundle="$repo/edge/descriptor/target/protobuf/api-bundle.pb"

if [[ ! -f "$bundle" ]]; then
  echo "descriptor not built yet -- run: ./mvnw -pl edge/descriptor -am package -DskipTests" >&2
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$bundle" "$work/api-bundle.pb"

# protoc-gen-openapiv2 is a Go tool and insists on a Go import path per file
# even for OpenAPI output, hence the M<file>=<dummy path> args. They never
# appear in the JSON.
docker run --rm -v "$work:/w" -w /w golang:1.23-alpine sh -eu -c '
  apk add --no-cache protobuf >/dev/null
  go install github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2@v2.27.1
  export PATH=$PATH:/root/go/bin
  M="Mshortener/api/shortener_api.proto=x/s,Mauth/api/auth_api.proto=x/a,Muser/api/user_api.proto=x/u,Mgoogle/api/annotations.proto=x/n,Mgoogle/api/http.proto=x/h,Mgoogle/protobuf/descriptor.proto=x/d"
  protoc \
    --descriptor_set_in=api-bundle.pb \
    --openapiv2_out=. \
    --openapiv2_opt=allow_merge=true,merge_file_name=api,json_names_for_fields=true,$M \
    shortener/api/shortener_api.proto auth/api/auth_api.proto user/api/user_api.proto
'

# Fix up the merged doc:
#  - info: protoc-gen-openapiv2 fills title/description from the first proto's
#    file comment, which here is a note about package naming. Replace it.
#  - securityDefinitions: a Bearer scheme so Postman wires an Authorization
#    header field. Applied globally -- harmless on the public endpoints,
#    required on the gated ones.
docker run --rm -v "$work:/w" -w /w golang:1.23-alpine sh -eu -c '
  apk add --no-cache jq >/dev/null
  jq "
    .info = {\"title\": \"gRPC URL Shortener -- edge API\", \"description\": \"HTTP/JSON surface of the shortener/auth/user services, transcoded by the edge Envoy. Generated from the .proto contract; do not edit by hand.\", \"version\": \"0.1.0\"}
    | .host = \"localhost:8080\"
    | .schemes = [\"http\"]
    | .securityDefinitions = {\"Bearer\": {\"type\": \"apiKey\", \"name\": \"Authorization\", \"in\": \"header\"}}
    | .security = [{\"Bearer\": []}]
  " api.swagger.json > api.swagger.json.tmp
  mv api.swagger.json.tmp api.swagger.json
'

cp "$work/api.swagger.json" "$here/api.swagger.json"
echo "wrote $here/api.swagger.json"
