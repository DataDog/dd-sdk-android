#!/usr/bin/env bash

local_ci_usage="Usage: local_ci.sh [-s|--setup] [-n|--clean] [-a|--analysis] [-c|--compile] [-t|--test] [-h|--help]"

SETUP=0
CLEANUP=0
ANALYSIS=0
COMPILE=0
TEST=0
KTLINT_VERSION=1.5.0
DETEKT_VERSION=1.23.8

export CI=true

while [[ $# -gt 0 ]]; do
  case $1 in
  -s | --setup)
    SETUP=1
    shift
    ;;
  -n | --clean)
    CLEANUP=1
    shift
    ;;
  -a | --analysis)
    ANALYSIS=1
    shift
    ;;
  -c | --compile)
    COMPILE=1
    shift
    ;;
  -t | --test)
    TEST=1
    shift
    ;;
  -h | --help)
    echo "$local_ci_usage"
    shift
    ;;
  *)
    echo "unknown arg: $1"
    echo "$local_ci_usage"
    exit 1
    ;;
  esac
done

# exit on errors
set -e

if [[ $SETUP == 1 ]]; then
  echo "-- SETUP"

  echo "---- Install KtLint $KTLINT_VERSION"
  TARGET_KTLINT=$(command -v ktlint || echo "/usr/local/bin/ktlint")
  sudo rm -f "$TARGET_KTLINT"
  curl -sSLO https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint
  chmod a+x ktlint
  sudo mv ktlint "$TARGET_KTLINT"
  hash -r
  echo "  installed at $TARGET_KTLINT"

  echo "---- Install Detekt $DETEKT_VERSION"
  TARGET_DETEKT=$(command -v detekt || echo "/usr/local/bin/detekt")
  sudo rm -f "$TARGET_DETEKT"
  curl -sSLO https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar
  sudo mv detekt-cli-$DETEKT_VERSION-all.jar /usr/local/lib/detekt-cli.jar
  sudo tee "$TARGET_DETEKT" > /dev/null <<'EOF'
#!/usr/bin/env bash
exec java -jar /usr/local/lib/detekt-cli.jar "$@"
EOF
  sudo chmod a+x "$TARGET_DETEKT"
  hash -r
  echo "  installed at $TARGET_DETEKT"
fi

if [[ $CLEANUP == 1 ]]; then
  echo "-- CLEANUP"

  echo "---- Clean repository"
  ./gradlew clean
  ./gradlew --stop
fi

if [[ $ANALYSIS == 1 ]]; then
  echo "-- STATIC ANALYSIS"

  echo "---- KtLint (changed files only)"
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  if [ "$CURRENT_BRANCH" = "develop" ]; then
    # On develop: check uncommitted + staged changes
    CHANGED_KT_FILES=$(git diff --name-only --diff-filter=d HEAD -- '*.kt' '*.kts' | grep -v 'build/generated/' | grep -v 'build/kspCaches/' || true)
  else
    # On feature branch: check all changes vs develop (committed + uncommitted)
    CHANGED_KT_FILES=$( (git diff --name-only --diff-filter=d develop... -- '*.kt' '*.kts'; git diff --name-only --diff-filter=d HEAD -- '*.kt' '*.kts') | sort -u | grep -v 'build/generated/' | grep -v 'build/kspCaches/' || true)
  fi
  if [ -n "$CHANGED_KT_FILES" ]; then
    echo "$CHANGED_KT_FILES" | xargs ktlint -F
  else
    echo "  No changed .kt/.kts files, skipping"
  fi

  echo "---- Detekt"
  ./gradlew detekt --continue

  if [[ $COMPILE == 1 ]]; then
    # Assemble is required to get generated classes type resolution
    echo "------ Assemble Libraries & Build Detekt custom rules"
    ./gradlew assembleLibrariesDebug :tools:detekt:jar
    ./gradlew printSdkDebugRuntimeClasspath --no-parallel
    classpath=$(cat sdk_classpath)

    echo "------ Detekt test pyramid rules"
    rm -f apiSurface.log apiUsage.log
    detekt --parallel --config detekt_test_pyramid.yml --plugins tools/detekt/build/libs/detekt.jar -cp "$classpath" --jvm-target 11 -ex "**/*.kts"

    set +e
    grep -v -f apiUsage.log apiSurface.log > apiCoverageMiss.log
    grep -f apiUsage.log apiSurface.log > apiCoverageHit.log
    set -e

    surfaceCount=$(sed -n '$=' apiSurface.log)
    coverageMissCount=$(sed -n '$=' apiCoverageMiss.log)
    coverageHitCount=$(sed -n '$=' apiCoverageHit.log)
    if [ -s "apiCoverageMiss.log" ] && [ "${surfaceCount:-0}" -gt 0 ]; then
      hitPercent=$(( (coverageHitCount * 100) / surfaceCount ))
      missPercent=$(( (coverageMissCount * 100) / surfaceCount ))
      echo "⚠ Test Integration coverage missed ${coverageMissCount} apis ($hitPercent % coverage; $missPercent % miss)"
    else
      echo "✔ Test Integration coverage 100%"
    fi

  else
    echo "------ Detekt Custom Rules & API Coverage ignored, run again with --analysis --compile"
  fi

  echo "---- AndroidLint"
  ./gradlew :lintCheckAll

  echo "---- 3rd Party License"
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  if [ "$CURRENT_BRANCH" = "develop" ]; then
    DEPS_CHANGED=$(git diff --name-only HEAD -- 'gradle/libs.versions.toml' '**/build.gradle.kts' || true)
  else
    DEPS_CHANGED=$(git diff --name-only develop... -- 'gradle/libs.versions.toml' '**/build.gradle.kts' || true)
  fi
  if [ -n "$DEPS_CHANGED" ]; then
    ./gradlew checkDependencyLicenses
  else
    echo "  No dependency changes"
  fi
fi

if [[ $COMPILE == 1 ]]; then
  echo "-- COMPILATION"

  echo "---- Assemble Libraries, Unit Tests & Instrumentation APKs"
  ./gradlew assembleLibrariesDebug assembleDebugUnitTest :instrumented:integration:assembleDebugAndroidTest
fi

if [[ $TEST == 1 ]]; then
  echo "---- Unit tests (Debug & Release)"
  ./gradlew uTD uTR
fi

unset CI
echo "-- Done ✔︎"
