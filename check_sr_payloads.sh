#!/usr/bin/env sh

CHECK_TIMESTAMPS_ORDER=0

while [[ $# -gt 0 ]]; do
  case $1 in
  --timestamp_order)
    CHECK_TIMESTAMPS_ORDER=1
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

if [[ $CHECK_TIMESTAMPS_ORDER == 1 ]]; then

  PAYLOAD_OUTPUT_PATH="storage/emulated/0/Android/data/com.datadog.android.sample/cache/session_replay"

  echo "-- CHECKING TIMESTAMPS ORDER"
  adb pull $PAYLOAD_OUTPUT_PATH
  adb shell rm -rf $PAYLOAD_OUTPUT_PATH
  python3 check_sr_output.py session_replay
fi

echo "-- Done ✔︎"
