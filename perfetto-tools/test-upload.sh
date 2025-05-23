#!/bin/bash

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

# Check if file path is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <path_to_trace_file>"
    exit 1
fi

# Configuration
API_KEY=os.getenv('DD_API_KEY')
SERVER_URL="http://localhost:8080/api/v2/profile"
SERVICE="test-service"
VERSION="1.0.0"
SDK_VERSION="1.0.0"

# Create the JSON object directly
EVENT_JSON='{
    "attachments": ["cpu.pprof"],
    "tags_profiler": "service:'$SERVICE',version:'$VERSION'",
    "family": "go",
    "version": "4"
}'

# Make the POST request using curl
curl -X POST \
    -H "DD-API-KEY: $API_KEY" \
    -H "DD-EVP-ORIGIN: dd-sdk-android" \
    -H "DD-EVP-ORIGIN-VERSION: $SDK_VERSION" \
    -F "cpu.pprof=@$1" \
    -F "event=$EVENT_JSON" \
    "$SERVER_URL" 