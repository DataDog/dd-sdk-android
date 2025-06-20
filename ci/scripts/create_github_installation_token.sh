#!/usr/bin/env bash

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

set -o pipefail

client_id=$1
installation_id=$2

now=$(date +%s)
iat=$((${now} - 60)) # Issues 60 seconds in the past
exp=$((${now} + 600)) # Expires 10 minutes in the future

b64enc() { openssl base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n'; }

header_json='{
    "typ":"JWT",
    "alg":"RS256"
}'
# Header encode
header=$(echo -n "${header_json}" | b64enc)

payload_json="{
    \"iat\":${iat},
    \"exp\":${exp},
    \"iss\":\"${client_id}\"
}"

# Payload encode
payload=$(echo -n "${payload_json}" | b64enc)

# Signature
header_payload="${header}"."${payload}"
signature=$(openssl dgst -sha256 -sign /dev/stdin <(echo -n "${header_payload}") | b64enc)

# Create JWT
jwt_token="${header_payload}"."${signature}"

# Fetch installation token
installation_token=$(curl \
  -s \
  -X POST \
  -H "Authorization: Bearer $jwt_token" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/app/installations/$installation_id/access_tokens)

echo $installation_token | jq -r '.token'
