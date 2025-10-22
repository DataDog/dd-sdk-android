#!/usr/bin/env sh

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
  INSTALL_KTLINT=false
  if [[ -x "$(command -v ktlint)" ]]; then
      INSTALLED_KTLINT=`ktlint --version`
      echo "  KtLint already installed; version $INSTALLED_KTLINT"
      if [[ $INSTALLED_KTLINT != $KTLINT_VERSION ]]; then
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
  rm -rf dd-sdk-android-internal/build/
  rm -rf dd-sdk-android-core/build/
  rm -rf features/dd-sdk-android-logs/build/
  rm -rf features/dd-sdk-android-ndk/build/
  rm -rf features/dd-sdk-android-rum/build/
  rm -rf features/dd-sdk-android-session-replay/build/
  rm -rf features/dd-sdk-android-session-replay-compose/build/
  rm -rf features/dd-sdk-android-session-replay-material/build/
  rm -rf features/dd-sdk-android-trace/build/
  rm -rf features/dd-sdk-android-trace-otel/build/
  rm -rf features/dd-sdk-android-webview/build/
  rm -rf integrations/dd-sdk-android-coil/build/
  rm -rf integrations/dd-sdk-android-compose/build/
  rm -rf integrations/dd-sdk-android-fresco/build/
  rm -rf integrations/dd-sdk-android-glide/build/
  rm -rf integrations/dd-sdk-android-rum-coroutines/build/
  rm -rf integrations/dd-sdk-android-trace-coroutines/build/
  rm -rf integrations/dd-sdk-android-okhttp/build/
  rm -rf integrations/dd-sdk-android-okhttp-otel/build/
  rm -rf integrations/dd-sdk-android-rx/build/
  rm -rf integrations/dd-sdk-android-sqldelight/build/
  rm -rf integrations/dd-sdk-android-timber/build/
  rm -rf integrations/dd-sdk-android-tv/build/

  ./gradlew --stop
fi

if [[ $ANALYSIS == 1 ]]; then
  echo "-- STATIC ANALYSIS"

  echo "---- KtLint"
  ktlint -F "**/*.kt" "**/*.kts" '!**/build/generated/**' '!**/build/kspCaches/**'

  echo "---- Detekt"
  if [ -z $DD_SOURCE ]; then
    echo "Can't run shared Detekt, missing dd_source repository path."
    echo "Please set the path to your local dd_source repository in the DD_SOURCE environment variable."
    echo "E.g.: "
    echo "$ export DD_SOURCE=/Volumes/Dev/ci/dd-source"
    exit 1
  else
    echo "Using Detekt rules from $DD_SOURCE folder"
  fi

  echo "------ Detekt common rules"
  detekt --config "$DD_SOURCE/domains/mobile/config/android/gitlab/detekt/detekt-common.yml"

  echo "------ Detekt public API rules"
  detekt --config "$DD_SOURCE/domains/mobile/config/android/gitlab/detekt/detekt-public-api.yml"

  if [[ $COMPILE == 1 ]]; then
    # Assemble is required to get generated classes type resolution
    echo "------ Assemble Libraries"
    ./gradlew assembleLibrariesDebug
    ./gradlew printSdkDebugRuntimeClasspath
    classpath=$(cat sdk_classpath)

    echo "------ Build Detekt custom rules"
    ./gradlew :tools:detekt:jar

    # TODO RUM-628 Switch to Java 17 bytecode
    echo "------ Detekt custom rules"
    detekt --config detekt_custom_general.yml,detekt_custom_safe_calls.yml,detekt_custom_unsafe_calls.yml --plugins tools/detekt/build/libs/detekt.jar -cp "$classpath" --jvm-target 11 -ex "**/*.kts"

    echo "------ Detekt test pyramid rules"
    rm apiSurface.log apiUsage.log
    detekt --config detekt_test_pyramid.yml --plugins tools/detekt/build/libs/detekt.jar -cp "$classpath" --jvm-target 11 -ex "**/*.kts"

    grep -v -f apiUsage.log apiSurface.log > apiCoverageMiss.log
    grep -f apiUsage.log apiSurface.log > apiCoverageHit.log
    if [ ! -s "${FILENAME}" ]; then
      surfaceCount=`sed -n '$=' apiSurface.log`
      coverageMissCount=`sed -n '$=' apiCoverageMiss.log`
      coverageHitCount=`sed -n '$=' apiCoverageHit.log`
      hitPercent=$(( (coverageHitCount * 100)/surfaceCount ))
      missPercent=$(( (coverageMissCount * 100)/surfaceCount ))
      echo "✘ Test Integration coverage missed ${coverageMissCount} apis ($hitPercent % coverage; $missPercent % miss)"
      exit 1
    else
      echo "✔ Test Integration coverage 100%"
    fi

  else
    echo "------ Detekt Custom Rules & API Coverage ignored, run again with --analysis --compile"
  fi

  echo "---- AndroidLint"
  ./gradlew :lintCheckAll

  echo "---- 3rd Party License"
  ./gradlew checkDependencyLicenses
fi

if [[ $COMPILE == 1 ]]; then
  echo "-- COMPILATION"

  echo "---- Assemble Libraries"
  ./gradlew assembleLibrariesDebug

  echo "---- Assemble Unit Tests"
  ./gradlew assembleDebugUnitTest

  echo "---- Assemble Android Instrumentation APKs"
  ./gradlew :instrumented:integration:assembleDebugAndroidTest
fi

if [[ $TEST == 1 ]]; then
  echo "---- Unit tests (Debug)"
  ./gradlew uTD

  echo "---- Unit tests (Release)"
  ./gradlew uTR
fi

unset CI
echo "-- Done ✔︎"
