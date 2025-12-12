#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

source ./ci/scripts/vault_config.sh
source ./ci/scripts/get-secret.sh

: > ./ci/pipelines/secrets.env

# Gradle properties
echo "GRADLE_PROPERTIES=$(get_secret $DD_ANDROID_SECRET__GRADLE_PROPERTIES | base64)" >> ./ci/pipelines/secrets.env

# Signing and publishing
#echo "SIGNING_GPG_PRIVATE_KEY=$(get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PRIVATE_KEY)" >> ./ci/pipelines/secrets.env
#echo "SIGNING_GPG_PASSPHRASE=$(get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PASSPHRASE)" >> ./ci/pipelines/secrets.env
#echo "SIGNING_GPG_PUBLIC_KEY=$(get_secret $DD_ANDROID_SECRET__SIGNING_GPG_PUBLIC_KEY)" >> ./ci/pipelines/secrets.env
#echo "PUBLISHING_CENTRAL_USERNAME=$(get_secret $DD_ANDROID_SECRET__PUBLISHING_CENTRAL_USERNAME)" >> ./ci/pipelines/secrets.env
#echo "PUBLISHING_CENTRAL_PASSWORD=$(get_secret $DD_ANDROID_SECRET__PUBLISHING_CENTRAL_PWD)" >> ./ci/pipelines/secrets.env

# API and App keys
echo "DD_API_KEY=$(get_secret $DD_ANDROID_SECRET__API_KEY)" >> ./ci/pipelines/secrets.env
echo "DD_APP_KEY=$(get_secret $DD_ANDROID_SECRET__APP_KEY)" >> ./ci/pipelines/secrets.env
#echo "CODECOV_TOKEN=$(get_secret $DD_ANDROID_SECRET__CODECOV_TOKEN)" >> ./ci/pipelines/secrets.env

# Keystore
#echo "KEYSTORE=$(get_secret $DD_ANDROID_SECRET__KEYSTORE | base64)" >> ./ci/pipelines/secrets.env
#echo "KEYSTORE_PASSWORD=$(get_secret $DD_ANDROID_SECRET__KEYSTORE_PWD)" >> ./ci/pipelines/secrets.env

# E2E Testing
#echo "E2E_CONFIG_JSON=$(get_secret $DD_ANDROID_SECRET__E2E_CONFIG_JSON | base64)" >> ./ci/pipelines/secrets.env
echo "E2E_API_KEY=$(get_secret $DD_ANDROID_SECRET__E2E_API_KEY)" >> ./ci/pipelines/secrets.env
echo "E2E_APP_KEY=$(get_secret $DD_ANDROID_SECRET__E2E_APP_KEY)" >> ./ci/pipelines/secrets.env
echo "E2E_MOBILE_APP_ID=$(get_secret $DD_ANDROID_SECRET__E2E_MOBILE_APP_ID)" >> ./ci/pipelines/secrets.env

# WebView
#echo "WEBVIEW_CONFIG_JSON=$(get_secret $DD_ANDROID_SECRET__WEBVIEW_CONFIG_JSON | base64)" >> ./ci/pipelines/secrets.env
echo "WEBVIEW_API_KEY=$(get_secret $DD_ANDROID_SECRET__WEBVIEW_API_KEY)" >> ./ci/pipelines/secrets.env
echo "WEBVIEW_APP_KEY=$(get_secret $DD_ANDROID_SECRET__WEBVIEW_APP_KEY)" >> ./ci/pipelines/secrets.env
echo "WEBVIEW_MOBILE_APP_ID=$(get_secret $DD_ANDROID_SECRET__WEBVIEW_MOBILE_APP_ID)" >> ./ci/pipelines/secrets.env

# Staging
#echo "E2E_STAGING_CONFIG_JSON=$(get_secret $DD_ANDROID_SECRET__E2E_STAGING_CONFIG_JSON | base64)" >> ./ci/pipelines/secrets.env
echo "E2E_STAGING_API_KEY=$(get_secret $DD_ANDROID_SECRET__E2E_STAGING_API_KEY)" >> ./ci/pipelines/secrets.env
echo "E2E_STAGING_APP_KEY=$(get_secret $DD_ANDROID_SECRET__E2E_STAGING_APP_KEY)" >> ./ci/pipelines/secrets.env
echo "E2E_STAGING_APP_ID=$(get_secret $DD_ANDROID_SECRET__E2E_STAGING_APP_ID)" >> ./ci/pipelines/secrets.env

# Benchmark
#echo "BENCHMARK_CONFIG_JSON=$(get_secret $DD_ANDROID_SECRET__BENCHMARK_CONFIG_JSON | base64)" >> ./ci/pipelines/secrets.env
echo "BENCHMARK_API_KEY=$(get_secret $DD_ANDROID_SECRET__BENCHMARK_API_KEY)" >> ./ci/pipelines/secrets.env
echo "BENCHMARK_APP_KEY=$(get_secret $DD_ANDROID_SECRET__BENCHMARK_APP_KEY)" >> ./ci/pipelines/secrets.env
echo "BENCHMARK_MOBILE_APP_ID=$(get_secret $DD_ANDROID_SECRET__BENCHMARK_MOBILE_APP_ID)" >> ./ci/pipelines/secrets.env
