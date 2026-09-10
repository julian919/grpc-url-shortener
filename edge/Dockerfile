# The descriptor is a BUILD ARTEFACT of shortener/api, not a file anyone edits,
# so it is produced here by the same Maven build the services use rather than
# mounted in from the host. That is what keeps the edge honest: you cannot ship
# an edge image whose schema disagrees with the proto it was built from.
#
# Cost of this arrangement: every proto change requires rebuilding this image
# too (`docker compose up -d --build edge`). Envoy reads the descriptor once, at
# config load, and has no reload-on-change mechanism.
FROM eclipse-temurin:21-jdk AS descriptor
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl edge/descriptor -am package -DskipTests

FROM envoyproxy/envoy:v1.34-latest
COPY --from=descriptor /workspace/edge/descriptor/target/protobuf/api-bundle.pb /etc/envoy/api-bundle.pb
COPY edge/envoy.yaml /etc/envoy/envoy.yaml
EXPOSE 8080 9901
CMD ["envoy", "-c", "/etc/envoy/envoy.yaml"]
