#!/usr/bin/env bash

local_ci_usage="Usage: local_ci.sh [-s|--setup] [-n|--clean] [-a|--analysis] [-c|--compile] [-t|--test] [-h|--help]"

SETUP=0
CLEANUP=0
ANALYSIS=0
COMPILE=0
TEST=0
KTLINT_VERSION=0.50.0

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

  echo "---- Install KtLint"
  INSTALL_KTLINT=false
  if [[ -x "$(command -v ktlint)" ]]; then
      INSTALLED_KTLINT=$(ktlint --version)
      echo "  KtLint already installed; version $INSTALLED_KTLINT"
      if [[ $INSTALLED_KTLINT != "$KTLINT_VERSION" ]]; then
        echo "  Upgrading to version $KTLINT_VERSION"
        INSTALL_KTLINT=true
      fi
  fi

  if [[ $INSTALL_KTLINT = true ]]; then
    curl -SLO https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint && chmod a+x ktlint
    sudo mv ktlint /usr/local/bin/
    echo "  KtLint installed; version $(ktlint --version)"
  fi

  echo "---- Install Detekt"
  if [[ -x "$(command -v detekt)" ]]; then
      echo "  Detekt already installed; version $(detekt --version)"
      read -p "  Would you like to update Detekt? " -n 1 -r
      echo
      if [[ $REPLY =~ ^[Yy]$ ]]
      then
        brew upgrade detekt
        echo "  Detekt upgraded; version $(detekt --version)"
      fi
  else
    brew install detekt
    echo "  Detekt installed; version $(detekt --version)"
  fi
fi

if [[ $CLEANUP == 1 ]]; then
  echo "-- CLEANUP"

  echo "---- Clean repository"
  ./gradlew clean
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
  if [ -z "$DD_SOURCE" ]; then
    echo "Can't run shared Detekt, missing dd_source repository path."
    echo "Please set the path to your local dd_source repository in the DD_SOURCE environment variable."
    echo "E.g.: "
    echo "$ export DD_SOURCE=/Volumes/Dev/ci/dd-source"
    exit 1
  else
    echo "Using Detekt rules from $DD_SOURCE folder"
  fi

  echo "------ Detekt common rules"
  detekt --parallel --config "$DD_SOURCE/domains/mobile/config/android/gitlab/detekt/detekt-common.yml"

  echo "------ Detekt public API rules"
  detekt --parallel --config "$DD_SOURCE/domains/mobile/config/android/gitlab/detekt/detekt-public-api.yml"

  if [[ $COMPILE == 1 ]]; then
    # Assemble is required to get generated classes type resolution
    echo "------ Assemble Libraries & Build Detekt custom rules"
    ./gradlew assembleLibrariesDebug printSdkDebugRuntimeClasspath :tools:detekt:jar
    classpath=$(cat sdk_classpath)

    # TODO RUM-628 Switch to Java 17 bytecode
    echo "------ Detekt custom rules"
    detekt --parallel --config detekt_custom_general.yml,detekt_custom_safe_calls.yml,detekt_custom_unsafe_calls.yml --plugins tools/detekt/build/libs/detekt.jar -cp "$classpath" --jvm-target 11 -ex "**/*.kts"

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
