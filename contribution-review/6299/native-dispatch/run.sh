#!/usr/bin/env bash
# Frozen-source comparison of automatic subtype query registration.
# No publishing or deployment. Strict runtime checks and probe assertions retained.
set -euo pipefail
root="$(pwd)"
evidence="$root/evidence"
mkdir -p "$evidence/config" "$root/probe-classes" "$root/generated" "$root/feature-classes" "$root/feature-sdk"
exec > >(tee "$evidence/diagnostic.log") 2>&1
printf 'Started: '; date -u +%FT%TZ
java --version
native-image --version
git -C "$root/diagnostic" rev-parse HEAD HEAD^{tree} | tee "$evidence/diagnostic-revision.txt"
for variant in baseline candidate; do
  git -C "$root/$variant" rev-parse HEAD HEAD^{tree} | tee "$evidence/$variant-revision.txt"
  (
    cd "$root/$variant"
    # Harness packaging only; prior full verify evidence remains separate.
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
cmp "$evidence/baseline-dependencies.sha256" "$evidence/candidate-dependencies.sha256"
source_dir="$root/diagnostic/contribution-review/6299/native-dispatch"
cp "$source_dir/NativeDispatchProbe.java" "$source_dir/IterableContentsFeature.java" "$source_dir/run.sh" "$evidence/"
# The seven original probe cases and assertions are not edited. Also run an
# equivalent relocated application, unknown to the feature implementation.
python3 - "$evidence" "$root/generated" <<'PY'
import hashlib, pathlib, sys
source, generated = map(pathlib.Path, sys.argv[1:])
probe = (source / 'NativeDispatchProbe.java').read_bytes()
assert hashlib.sha256(probe).hexdigest() == 'd7094247a6a72289a289877a71e530d586f35b70bdc647dbab899327d84c8de3'
relocated = probe.decode().replace('package validation;', 'package relocated;').replace('NativeDispatchProbe', 'RelocatedDispatchProbe')
(generated / 'RelocatedDispatchProbe.java').write_text(relocated)
(generated / 'DispatchApplications.java').write_text('''package validation;
public final class DispatchApplications {
    public static void main(String[] args) {
        if (args.length == 1) {
            NativeDispatchProbe.main(args);
            relocated.RelocatedDispatchProbe.main(args);
            return;
        }
        if (args.length != 3) throw new IllegalArgumentException("variant application case");
        String[] forwarded = { args[0], args[2] };
        if (args[1].equals("original")) NativeDispatchProbe.main(forwarded);
        else if (args[1].equals("relocated")) relocated.RelocatedDispatchProbe.main(forwarded);
        else throw new IllegalArgumentException("Unknown application");
    }
}
''')
(generated / 'UnusedIterableSerializer.java').write_text('''package validation;
public final class UnusedIterableSerializer extends tools.jackson.databind.ser.jdk.IterableSerializer {
    public UnusedIterableSerializer() { super(null, false, null); }
}
''')
for path in generated.glob('*.java'):
    (source / path.name).write_bytes(path.read_bytes())
feature = (source / 'IterableContentsFeature.java').read_text()
assert 'NativeDispatchProbe' not in feature and 'relocated' not in feature
PY
sha256sum "$evidence/"*.java > "$evidence/java-sources.sha256"
javac --release 17 -cp "$(cat "$root/baseline-classpath.txt")" -d "$root/probe-classes" \
  "$source_dir/NativeDispatchProbe.java" "$root/generated/"*.java
# Compile a separate hosted feature against the pinned SDK, not a Jackson dependency.
(
  cd "$root/candidate"
  ./mvnw -B -ff -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact=org.graalvm.sdk:nativeimage:25.0.1 -DoutputDirectory="$root/feature-sdk"
) 2>&1 | tee "$evidence/feature-sdk.log"
sdk="$root/feature-sdk/nativeimage-25.0.1.jar"
sha256sum "$sdk" > "$evidence/feature-sdk.sha256"
javac --release 17 -cp "$sdk" -d "$root/feature-classes" "$source_dir/IterableContentsFeature.java"
jar --create --file "$root/iterable-contents-feature.jar" -C "$root/feature-classes" .
sha256sum "$root/iterable-contents-feature.jar" > "$evidence/feature-jar.sha256"
cp "$root/iterable-contents-feature.jar" "$evidence/"
# Collect common metadata on original production only, for both applications.
java -agentlib:native-image-agent=config-output-dir="$evidence/config" \
  -cp "$root/probe-classes:$(cat "$root/baseline-classpath.txt")" \
  validation.DispatchApplications baseline 2>&1 | tee "$evidence/baseline-agent.log"
java -cp "$root/probe-classes:$(cat "$root/candidate-classpath.txt")" \
  validation.DispatchApplications candidate 2>&1 | tee "$evidence/candidate-jvm.log"
find "$evidence/config" -type f -print0 | sort -z | xargs -0 sha256sum > "$evidence/common-metadata.sha256"
# Reject accidentally trained subclass metadata before the native comparison.
python3 - "$evidence/config" <<'PY'
import pathlib, sys
text = '\n'.join(p.read_text() for p in pathlib.Path(sys.argv[1]).rglob('*.json'))
for name in ('CustomContentsSerializer', 'InheritedCustomSerializer', 'InheritedContentsSerializer'):
    assert name not in text, 'Baseline metadata unexpectedly contains ' + name
PY
printf 'arm\tapplication\tcase\texit_code\n' > "$evidence/cases.tsv"
cases=(custom-empty custom-suffix inherited-custom inherited-empty inherited-kept filtering-disabled builtin-empty)
failed=0
for arm in baseline candidate candidate-with-auto-feature; do
  variant="$arm"
  feature_args=()
  if [ "$arm" = candidate-with-auto-feature ]; then
    variant=candidate
    feature_args=(--features=validation.IterableContentsFeature)
  fi
  # Same feature JAR is on all classpaths; only the feature switch differs.
  native-image -J-Xmx4g -O0 --no-fallback --exact-reachability-metadata \
    -H:ConfigurationFileDirectories="$evidence/config" "${feature_args[@]}" \
    -cp "$root/iterable-contents-feature.jar:$root/probe-classes:$(cat "$root/$variant-classpath.txt")" \
    validation.DispatchApplications "$root/$arm-native" 2>&1 | tee "$evidence/$arm-build.log"
  sha256sum "$root/$arm-native" > "$evidence/$arm-binary.sha256"
  for application in original relocated; do
    for name in "${cases[@]}"; do
      if "$root/$arm-native" -XX:MissingRegistrationReportingMode=Exit "$variant" "$application" "$name" \
          > "$evidence/$arm-$application-$name.log" 2>&1; then
        status=0
      else
        status=$?
        failed=1
      fi
      cat "$evidence/$arm-$application-$name.log"
      printf '%s\t%s\t%s\t%s\n' "$arm" "$application" "$name" "$status" | tee -a "$evidence/cases.tsv"
    done
  done
done
python3 - "$evidence" <<'PY'
import csv, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
with (root / 'cases.tsv').open() as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
summary = {'scope': 'Seven unchanged dispatch cases in two application namespaces; native integration only', 'arms': {}}
for arm in ('baseline', 'candidate', 'candidate-with-auto-feature'):
    cases = [r for r in rows if r['arm'] == arm]
    passed = sum(int(r['exit_code']) == 0 for r in cases)
    errors = sum('MissingReflectionRegistrationError' in
        (root / f"{arm}-{r['application']}-{r['case']}.log").read_text()
        for r in cases if int(r['exit_code']) != 0)
    summary['arms'][arm] = {'reported': len(cases), 'passed': passed,
        'nonzero_exit': len(cases)-passed, 'missing_reflection_errors': errors, 'cases': cases}
assert len(rows) == 42, 'Incomplete matrix'
prefix = 'AUTO-QUERY '
registered = sorted({line[len(prefix):].strip() for line in
    (root / 'candidate-with-auto-feature-build.log').read_text().splitlines() if line.startswith(prefix)})
summary['automatically_registered_types'] = registered
summary['unused_subclass_registered'] = 'validation.UnusedIterableSerializer' in registered
expected = {f'{app}${name}' for app in ('validation.NativeDispatchProbe', 'relocated.RelocatedDispatchProbe')
            for name in ('CustomContentsSerializer', 'InheritedCustomSerializer', 'InheritedContentsSerializer')}
summary['all_fixture_types_registered'] = expected.issubset(registered)
text = json.dumps(summary, indent=2) + '\n'
(root / 'summary.json').write_text(text)
print(text)
assert summary['all_fixture_types_registered'], 'Missing automatic registrations'
assert not summary['unused_subclass_registered'], 'Unreachable class was unnecessarily registered'
assert not any(line.startswith(prefix) for line in (root / 'candidate-build.log').read_text().splitlines()), 'Feature unexpectedly active in raw arm'
PY
printf 'Finished: '; date -u +%FT%TZ
# Raw-candidate failures stay visible; only the feature-enabled arm is a repair trial.
exit "$failed"
