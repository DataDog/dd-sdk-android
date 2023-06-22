#!/usr/bin/env sh

local_ci_usage="Usage: local_ci.sh [-s|--setup] [-n|--clean] [-a|--analysis] [-c|--compile] [-t|--test]"

SETUP=0
CLEANUP=0
ANALYSIS=0
COMPILE=0
TEST=0

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
    echo $local_ci_usage
    shift
    ;;
  *)
    echo "unknown arg: $1"
    echo $local_ci_usage
    exit 1
    ;;
  esac
done

# exit on errors
set -e

if [[ $SETUP == 1 ]]; then
  echo "-- SETUP"

  echo "---- Install KtLint"
  if [[ -x "$(command -v ktlint)" ]]; then
      echo "  KtLint already installed; version $(ktlint --version)"
  else
    curl -SLO https://github.com/pinterest/ktlint/releases/download/0.47.1/ktlint && chmod a+x ktlint
    sudo mv ktlint /usr/local/bin/
    echo "  KtLint installed; version $(ktlint --version)"
  fi

  echo "---- Install Detekt"
  if [[ -x "$(command -v detekt)" ]]; then
      echo "  Detekt already installed; version $(detekt --version)"
  else
    brew install detekt
    echo "  Detekt installed; version $(detekt --version)"
  fi
fi

if [[ $CLEANUP == 1 ]]; then
  echo "-- CLEANUP"

  echo "---- Clean repository"
  ./gradlew clean
  rm -rf dd-sdk-android-core/build/
  rm -rf features/dd-sdk-android-logs/build/
  rm -rf features/dd-sdk-android-ndk/build/
  rm -rf features/dd-sdk-android-rum/build/
  rm -rf features/dd-sdk-android-session-replay/build/
  rm -rf features/dd-sdk-android-session-replay-material/build/
  rm -rf features/dd-sdk-android-trace/build/
  rm -rf features/dd-sdk-android-webview/build/
  rm -rf integrations/dd-sdk-android-coil/build/
  rm -rf integrations/dd-sdk-android-compose/build/
  rm -rf integrations/dd-sdk-android-fresco/build/
  rm -rf integrations/dd-sdk-android-glide/build/
  rm -rf integrations/dd-sdk-android-ktx/build/
  rm -rf integrations/dd-sdk-android-okhttp/build/
  rm -rf integrations/dd-sdk-android-rx/build/
  rm -rf integrations/dd-sdk-android-sqldelight/build/
  rm -rf integrations/dd-sdk-android-timber/build/
  rm -rf integrations/dd-sdk-android-tv/build/

fi

if [[ $ANALYSIS == 1 ]]; then
  echo "-- STATIC ANALYSIS"

  echo "---- KtLint"
  ktlint -F "**/*.kt" "**/*.kts" '!**/build/generated/**' '!**/build/kspCaches/**'

  echo "---- Detekt common"
  detekt --config /Volumes/Dev/ci/dd-source/domains/mobile/config/android/gitlab/detekt/detekt-common.yml

  echo "---- Detekt public API"
  detekt --config /Volumes/Dev/ci/dd-source/domains/mobile/config/android/gitlab/detekt/detekt-public-api.yml


  if [[ $COMPILE == 1 ]]; then
    # Assemble is required to get generated classes type resolution
    echo "---- Assemble Library"
    ./gradlew assembleAll

    echo "---- Detekt Custom Rules"
    ./gradlew :tools:detekt:jar
    ./gradlew printSdkDebugRuntimeClasspath
    classpath=$(cat sdk_classpath)
    detekt --config detekt_custom.yml --plugins tools/detekt/build/libs/detekt.jar -cp "$classpath" --jvm-target 11 -ex "**/*.kts"
    # TODO RUMM-3263 Switch to Java 17 bytecode
  else
    echo "---- Detekt Custom Rules ignored, run again with --analysis --compile"
  fi

  echo "---- AndroidLint"
  ./gradlew :lintCheckAll

  echo "---- 3rd Party License"
  ./gradlew checkThirdPartyLicensesAll
fi

if [[ $COMPILE == 1 ]]; then
  echo "-- COMPILATION"

  echo "---- Assemble Library"
  ./gradlew assembleAll

  echo "---- Assemble Unit Tests"
  ./gradlew assembleDebugUnitTest

  echo "---- Assemble Android tests"
  ./gradlew :instrumented:nightly-tests:assembleDebugAndroidTest :instrumented:integration:assembleDebugAndroidTest
fi

if [[ $TEST == 1 ]]; then
  echo "---- Nightly test coverage"
  ./gradlew :checkNightlyTestsCoverage

  echo "---- Unit tests (Debug)"
  ./gradlew uTD

  echo "---- Unit tests (Relase)"
  ./gradlew uTR
fi

echo "-- Done ✔︎"
