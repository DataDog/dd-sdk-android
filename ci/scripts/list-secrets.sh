#!/bin/zsh

#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

source ./ci/scripts/vault_config.sh

list_secrets() {
    GREEN="\e[32m"
    RESET="\e[0m"

    echo "Available secrets:"
    for key in ${(k)DD_ANDROID_SECRETS}; do
        IFS=" | " read -r name description <<< "${DD_ANDROID_SECRETS[$key]}"
        echo "$key) ${GREEN}$name${RESET} - $description"
    done | sort -n

    echo ""
    echo "To add a new secret, first define it in 'ci/scripts/vault_config.sh' and retry."
}


select_secret() {
    echo
    while true; do
        echo "Enter the number of the secret you want to continue:"
        read "secret_number"
        if [[ -n ${DD_ANDROID_SECRETS[$secret_number]} ]]; then
            IFS=" | " read -r SECRET_NAME SECRET_DESC <<< "${DD_ANDROID_SECRETS[$secret_number]}"
            break
        else
            echo_err "Invalid selection. Please enter a valid number."
        fi
    done
}
