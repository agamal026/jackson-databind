#!/usr/bin/env bash
# Read-only diagnostic of two frozen Jackson revisions. No publishing or deployment.
set -euo pipefail
root="$(pwd)"
evidence="$root/evidence"
mkdir -p "$evidence/config" "$root/probe-classes"
exec > >(tee "$evidence/diagnostic.log") 2>&1
printf 'Started: '; date -u +%FT%TZ
java --version
native-image --version
for variant in baseline candidate; do
  git -C "$root/$variant" rev-parse HEAD
  (
    cd "$root/$variant"
    # Packaging for the native harness is separate from the already executed full verify gate.
    ./mvnw -B -ff -ntp -DskipTests package \
      org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
      -DincludeScope=runtime -Dmdep.outputFile="$root/$variant/runtime-classpath.txt"
  ) 2>&1 | tee "$evidence/$variant-package.log"
  cp="$root/$variant/target/classes:$(cat "$root/$variant/runtime-classpath.txt")"
  printf '%s' "$cp" > "$root/$variant-classpath.txt"
  tr ':' '\n' < "$root/$variant/runtime-classpath.txt" | while IFS= read -r jar || [ -n "$jar" ]; do
    sha256sum "$jar"
  done > "$evidence/$variant-dependencies.sha256"
  sha256sum "$root/$variant/src/main/java/tools/jackson/databind/ser/jdk/IterableSerializer.java" \
    > "$evidence/$variant-source.sha256"
done
probe="$root/diagnostic/contribution-review/6299/native-dispatch/NativeDispatchProbe.java"
cp "$probe" "$evidence/NativeDispatchProbe.java"
sha256sum "$probe" > "$evidence/probe.sha256"
javac --release 17 -cp "$(cat "$root/baseline-classpath.txt")" -d "$root/probe-classes" "$probe"
# Collect common metadata on the ORIGINAL implementation only. Do not train it on the new lookup.
java -agentlib:native-image-agent=config-output-dir="$evidence/config" \
  -cp "$root/probe-classes:$(cat "$root/baseline-classpath.txt")" \
  validation.NativeDispatchProbe baseline 2>&1 | tee "$evidence/baseline-agent.log"
java -cp "$root/probe-classes:$(cat "$root/candidate-classpath.txt")" \
  validation.NativeDispatchProbe candidate 2>&1 | tee "$evidence/candidate-jvm.log"
find "$evidence/config" -type f -print0 | sort -z | xargs -0 sha256sum > "$evidence/common-metadata.sha256"
for variant in baseline candidate; do
  native-image -J-Xmx4g -O0 --no-fallback --exact-reachability-metadata \
    -H:ConfigurationFileDirectories="$evidence/config" \
    -cp "$root/probe-classes:$(cat "$root/$variant-classpath.txt")" \
    validation.NativeDispatchProbe "$root/$variant-native" 2>&1 | tee "$evidence/$variant-build.log"
  # Exit mode prevents a blanket Throwable catch in any layer from hiding missing metadata.
  "$root/$variant-native" -XX:MissingRegistrationReportingMode=Exit "$variant" \
    2>&1 | tee "$evidence/$variant-native.log"
done
printf 'Finished: '; date -u +%FT%TZ
