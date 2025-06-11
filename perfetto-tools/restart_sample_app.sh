#!/bin/bash

PACKAGE_NAME="com.datadog.android.sample"
WAIT_TIME=30

echo "Starting process restart loop for $PACKAGE_NAME"
echo "Press [CTRL+C] to stop."

while true; do
    echo "----------------------------------------"
    echo "$(date): Force stopping $PACKAGE_NAME..."
    adb shell am force-stop "$PACKAGE_NAME"

    echo "$(date): Waiting for 2 seconds after stopping..."
    sleep 2

    echo "$(date): Starting $PACKAGE_NAME..."
    # Using monkey to launch the app's main activity
    adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1

    # Check if monkey command was successful (it usually returns quickly)
    if [ $? -eq 0 ]; then
        echo "$(date): Launch command sent for $PACKAGE_NAME."
    else
        echo "$(date): Failed to send launch command for $PACKAGE_NAME. Check if device is connected and package is installed."
    fi

    echo "$(date): Waiting for $WAIT_TIME seconds..."
    sleep "$WAIT_TIME"

    echo "$(date): Loop finished, restarting..."
done 