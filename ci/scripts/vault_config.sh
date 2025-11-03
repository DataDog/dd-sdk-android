#!/bin/zsh

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

DD_VAULT_ADDR=https://vault.us1.ddbuild.io
DD_ANDROID_SECRETS_PATH_PREFIX='kv/aws/arn:aws:iam::486234852809:role/ci-dd-sdk-android/'

DD_ANDROID_SECRET__TEST_SECRET="test.secret"
DD_ANDROID_SECRET__GRADLE_PROPERTIES="gradle.properties"
DD_ANDROID_SECRET__SIGNING_GPG_PRIVATE_KEY="signing.gpg_private_key"
DD_ANDROID_SECRET__SIGNING_GPG_PASSPHRASE="signing.gpg_passphrase"
DD_ANDROID_SECRET__SIGNING_GPG_PUBLIC_KEY="signing.gpg_public_key"
DD_ANDROID_SECRET__PUBLISHING_CENTRAL_USERNAME="publishing.central_username"
DD_ANDROID_SECRET__PUBLISHING_CENTRAL_PWD="publishing.central_password"
DD_ANDROID_SECRET__API_KEY="api_key"
DD_ANDROID_SECRET__APP_KEY="app_key"
DD_ANDROID_SECRET__CODECOV_TOKEN="codecov-token"
DD_ANDROID_SECRET__KEYSTORE="keystore"
DD_ANDROID_SECRET__KEYSTORE_PWD="keystore-password"
DD_ANDROID_SECRET__E2E_CONFIG_JSON="e2e_config_json"
DD_ANDROID_SECRET__E2E_API_KEY="e2e_api_key"
DD_ANDROID_SECRET__E2E_APP_KEY="e2e_app_key"
DD_ANDROID_SECRET__E2E_MOBILE_APP_ID="e2e_mobile_app_id"
DD_ANDROID_SECRET__E2E_STAGING_CONFIG_JSON="e2e_staging_config_json"
DD_ANDROID_SECRET__E2E_STAGING_API_KEY="e2e_staging_api_key"
DD_ANDROID_SECRET__E2E_STAGING_APP_KEY="e2e_staging_app_key"
DD_ANDROID_SECRET__E2E_STAGING_APP_ID="e2e_staging_mobile_app_id"
DD_ANDROID_SECRET__WEBVIEW_CONFIG_JSON="webview_config_json"
DD_ANDROID_SECRET__WEBVIEW_API_KEY="webview_api_key"
DD_ANDROID_SECRET__WEBVIEW_APP_KEY="webview_app_key"
DD_ANDROID_SECRET__WEBVIEW_MOBILE_APP_ID="webview_mobile_app_id"
DD_ANDROID_SECRET__BENCHMARK_CONFIG_JSON="benchmark_config_json"
DD_ANDROID_SECRET__BENCHMARK_API_KEY="benchmark_api_key"
DD_ANDROID_SECRET__BENCHMARK_APP_KEY="benchmark_app_key"
DD_ANDROID_SECRET__BENCHMARK_MOBILE_APP_ID="benchmark_mobile_app_id"

if [ "$CI" != "true" ]; then
idx=0
declare -A DD_ANDROID_SECRETS
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__TEST_SECRET | Test secret to verify functionality. Can be changed but not deleted."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__GRADLE_PROPERTIES | Content of the gradle.properties file, providing options to speed up CI jobs."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__SIGNING_GPG_PRIVATE_KEY | GPG private key for signing artifacts published to Sonatype Maven repository."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__SIGNING_GPG_PASSPHRASE | GPG passphrase for signing artifacts published to Sonatype Maven repository."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__SIGNING_GPG_PUBLIC_KEY | GPG public key for signing artifacts published to Sonatype Maven repository."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__PUBLISHING_CENTRAL_USERNAME | Username for publishing artifacts to Sonatype Maven repository."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__PUBLISHING_CENTRAL_PWD | Password for publishing artifacts to Sonatype Maven repository."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__API_KEY | API key for sending CI App reports to org2."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__APP_KEY | Application key for sending CI App reports to org2."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__CODECOV_TOKEN | CodeCov token for unit test jobs."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__KEYSTORE | Android signing keystore for building all APKs for synthetics."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__KEYSTORE_PWD | Android signing password for building all APKs for synthetics."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_CONFIG_JSON | config.json for uploading an end-to-end APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_API_KEY | API key for uploading an end-to-end APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_APP_KEY | App key for uploading an end-to-end APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_MOBILE_APP_ID | Application ID for uploading an end-to-end APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_STAGING_CONFIG_JSON | config.json for uploading an end-to-end APK to the Staging org."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_STAGING_API_KEY | API key for uploading an end-to-end APK to synthetics on the Staging org."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_STAGING_APP_KEY | App key for uploading an end-to-end APK to synthetics on the Staging org."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__E2E_STAGING_APP_ID | Application ID for uploading an end-to-end APK to synthetics on the Staging org."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__WEBVIEW_CONFIG_JSON | config.json for uploading an end-to-end APK (for webview integration) to the RUM Synthetics org (478292, https://rum-synthetics.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__WEBVIEW_API_KEY | API key for uploading an end-to-end APK (for webview integration) to the RUM Synthetics org (478292, https://rum-synthetics.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__WEBVIEW_APP_KEY | App key for uploading an end-to-end APK (for webview integration) to the RUM Synthetics org (478292, https://rum-synthetics.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__WEBVIEW_MOBILE_APP_ID | Application ID for uploading an end-to-end APK (for webview integration) to the RUM Synthetics org (478292, https://rum-synthetics.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__BENCHMARK_CONFIG_JSON | config.json for uploading a benchmark APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__BENCHMARK_API_KEY | API key for uploading a benchmark APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__BENCHMARK_APP_KEY | App key for uploading a benchmark APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
DD_ANDROID_SECRETS[$((idx++))]="$DD_ANDROID_SECRET__BENCHMARK_MOBILE_APP_ID | Application ID for uploading a benchmark APK to the Mobile Integration org (529432, https://mobile-integration.datadoghq.com/)."
fi
