#!/usr/bin/env bash
# Frozen-source native compatibility diagnostic. No publishing or deployment.
set -euo pipefail
root="$(pwd)"
evidence="$root/evidence"
mkdir -p "$evidence/config" "$evidence/query-metadata-control" "$root/probe-classes"
exec > >(tee "$evidence/diagnostic.log") 2>&1
printf 'Started: '; date -u +%FT%TZ
java --version
native-image --version
for variant in baseline candidate; do
  git -C "$root/$variant" rev-parse HEAD HEAD^{tree} | tee "$evidence/$variant-revision.txt"
  (
    cd "$root/$variant"
    # This packages the harness; it does not replace the prior full verify gate.
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
# Reject dependency drift between the paired executions, not just source drift.
cmp "$evidence/baseline-dependencies.sha256" "$evidence/candidate-dependencies.sha256"
probe="$root/diagnostic/contribution-review/6299/native-dispatch/NativeDispatchProbe.java"
cp "$probe" "$evidence/NativeDispatchProbe.java"
sha256sum "$probe" > "$evidence/probe.sha256"
javac --release 17 -cp "$(cat "$root/baseline-classpath.txt")" -d "$root/probe-classes" "$probe"
# Train only on original production. No candidate-specific lookups are recorded.
java -agentlib:native-image-agent=config-output-dir="$evidence/config" \
  -cp "$root/probe-classes:$(cat "$root/baseline-classpath.txt")" \
  validation.NativeDispatchProbe baseline 2>&1 | tee "$evidence/baseline-agent.log"
java -cp "$root/probe-classes:$(cat "$root/candidate-classpath.txt")" \
  validation.NativeDispatchProbe candidate 2>&1 | tee "$evidence/candidate-jvm.log"
find "$evidence/config" -type f -print0 | sort -z | xargs -0 sha256sum > "$evidence/common-metadata.sha256"
# Diagnostic control ONLY: register queries on the three runtime fixture types.
# The unaugmented candidate is tested separately; this is not a library fix.
cat > "$evidence/query-metadata-control/reachability-metadata.json" <<'JSON'
{
  "reflection": [
    {"type": "validation.NativeDispatchProbe$CustomContentsSerializer"},
    {"type": "validation.NativeDispatchProbe$InheritedCustomSerializer"},
    {"type": "validation.NativeDispatchProbe$InheritedContentsSerializer"}
  ]
}
JSON
sha256sum "$evidence/query-metadata-control/reachability-metadata.json" > "$evidence/query-metadata.sha256"
printf 'arm\tcase\texit_code\n' > "$evidence/cases.tsv"
cases=(custom-empty custom-suffix inherited-custom inherited-empty inherited-kept filtering-disabled builtin-empty)
failed=0
for arm in baseline candidate candidate-with-query-metadata; do
  variant="$arm"
  config="$evidence/config"
  if [ "$arm" = candidate-with-query-metadata ]; then
    variant=candidate
    config="$config,$evidence/query-metadata-control"
  fi
  native-image -J-Xmx4g -O0 --no-fallback --exact-reachability-metadata \
    -H:ConfigurationFileDirectories="$config" \
    -cp "$root/probe-classes:$(cat "$root/$variant-classpath.txt")" \
    validation.NativeDispatchProbe "$root/$arm-native" 2>&1 | tee "$evidence/$arm-build.log"
  sha256sum "$root/$arm-native" > "$evidence/$arm-binary.sha256"
  for name in "${cases[@]}"; do
    # Each probe gets a new process; an early strict-mode exit cannot hide later cases.
    if "$root/$arm-native" -XX:MissingRegistrationReportingMode=Exit "$variant" "$name" \
        > "$evidence/$arm-$name.log" 2>&1; then
      status=0
    else
      status=$?
      failed=1
    fi
    cat "$evidence/$arm-$name.log"
    printf '%s\t%s\t%s\n' "$arm" "$name" "$status" | tee -a "$evidence/cases.tsv"
  done
done
python3 - "$evidence" <<'PY'
import csv, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
with (root / 'cases.tsv').open() as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
summary = {'scope': 'Native dispatch diagnostic; not full suite or upstream readiness', 'arms': {}}
for arm in ('baseline', 'candidate', 'candidate-with-query-metadata'):
    cases = [r for r in rows if r['arm'] == arm]
    passed = sum(int(r['exit_code']) == 0 for r in cases)
    metadata_errors = sum('MissingReflectionRegistrationError' in
        (root / f"{arm}-{r['case']}.log").read_text() for r in cases if int(r['exit_code']) != 0)
    summary['arms'][arm] = {'reported': len(cases), 'passed': passed,
        'nonzero_exit': len(cases)-passed, 'missing_reflection_errors': metadata_errors,
        'cases': cases}
assert len(rows) == 21, 'Incomplete matrix'
text = json.dumps(summary, indent=2) + '\n'
(root / 'summary.json').write_text(text)
print(text)
PY
printf 'Finished: '; date -u +%FT%TZ
# Keep the job red if the raw candidate or any control fails. Never hide the regression.
exit "$failed"
