#!/usr/bin/env python3
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2019-Present Datadog, Inc
"""Download a snapshot of the public Rick & Morty API into benchmark assets.

Purpose: the benchmark sample app loads characters, locations, episodes and
character avatars from https://rickandmortyapi.com/api. The API is rate limited
and occasionally unavailable, which breaks benchmark runs. This script downloads
a complete snapshot of the data once, so it can be served locally by the
benchmark app itself (see the `rickandmorty` assets in this module).

Usage:
    python3 sample/benchmark/scripts/fetch_rick_and_morty.py [--force]
    python3 sample/benchmark/scripts/fetch_rick_and_morty.py --output-dir <dir>
    python3 sample/benchmark/scripts/fetch_rick_and_morty.py --avatar-pages N

Output (default, resolved relative to this script's location):
    sample/benchmark/src/main/assets/rickandmorty/
        characters.json   - JSON array of all characters, sorted by id
        locations.json    - JSON array of all locations, sorted by id
        episodes.json     - JSON array of all episodes, sorted by id
        avatars/{id}.jpeg - original avatar bytes for the selected characters

Entity objects are stored unmodified, so all `url`, `image`, `residents`,
`characters` and `episode` fields keep their original
`https://rickandmortyapi.com/api/...` values.

Avatars are bundled only for the first `--avatar-pages` pages (default 5, i.e.
the first 100 characters in API page order); pass `0` to download avatars for
every character. Avatar files outside the selected set are removed, so reruns
keep the folder consistent with the current flag value.

The CDN serves some avatars as PNG bytes under the `.jpeg` URL; the original
bytes are stored as-is and both JPEG and PNG signatures are accepted.

Already downloaded avatars (non-zero size) are skipped on reruns unless
`--force` is given, which makes retries after rate limiting cheap.
"""

import argparse
import contextlib
import http.client
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE_URL = "https://rickandmortyapi.com/api"
USER_AGENT = "benchmark-snapshot-fetcher/1.0"
TIMEOUT_SECONDS = 30
MAX_ATTEMPTS = 8
BACKOFF_INITIAL_SECONDS = 1.0
BACKOFF_MAX_SECONDS = 60.0
AVATAR_PAUSE_SECONDS = 0.05
DEFAULT_AVATAR_PAGES = 5

JPEG_MAGIC = b"\xff\xd8"
PNG_MAGIC = b"\x89PNG"

RESOURCES = {
    "character": "characters.json",
    "location": "locations.json",
    "episode": "episodes.json",
}


def log(message):
    # Short progress output on stderr.
    print(message, file=sys.stderr)


def has_image_signature(data):
    """True if the bytes start with a JPEG or PNG signature (the CDN serves both)."""
    return data[:2] == JPEG_MAGIC or data[:4] == PNG_MAGIC


def fetch_bytes(url):
    """Download a URL with retries, honoring Retry-After on 429/5xx."""
    # The URL is built from BASE_URL or read from the API's own `info.next`/
    # `image` fields; only https URLs are accepted below.
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ValueError(f"unsupported URL, expected https: {url}")
    delay = BACKOFF_INITIAL_SECONDS
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            # pi-lens-ignore: S310
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})  # nosec B310
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:  # nosec B310
                return response.read()
        except urllib.error.HTTPError as error:
            retry_after = error.headers.get("Retry-After") if error.headers else None
            if error.code == 429 or 500 <= error.code < 600:
                wait = delay
                if retry_after is not None:
                    with contextlib.suppress(ValueError):
                        wait = max(wait, float(retry_after))
                if attempt == MAX_ATTEMPTS:
                    raise
                log(f"  HTTP {error.code} for {url}, retrying in {wait:.0f}s "
                    f"(attempt {attempt}/{MAX_ATTEMPTS})")
                time.sleep(wait)
                delay = min(delay * 2, BACKOFF_MAX_SECONDS)
                continue
            raise
        # http.client.HTTPException covers IncompleteRead and RemoteDisconnected;
        # ConnectionResetError and other network failures are OSError subclasses.
        except (urllib.error.URLError, TimeoutError, OSError, http.client.HTTPException) as error:
            if attempt == MAX_ATTEMPTS:
                raise
            log(f"  network error for {url}: {error}, retrying in {delay:.0f}s "
                f"(attempt {attempt}/{MAX_ATTEMPTS})")
            time.sleep(delay)
            delay = min(delay * 2, BACKOFF_MAX_SECONDS)
    raise RuntimeError(f"unreachable: {url}")  # pragma: no cover


def fetch_json(url):
    try:
        return json.loads(fetch_bytes(url).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid JSON from {url}: {error}") from error


def download_resource(name):
    """Download all pages of a resource, following info.next.

    Returns (entities sorted by id, page index by id in API page order).
    """
    first = fetch_json(f"{BASE_URL}/{name}")
    expected_count = first["info"]["count"]
    ordered = []
    page_index_by_id = {}
    page = first
    page_index = 0
    while True:
        for entity in page["results"]:
            ordered.append(entity)
            page_index_by_id[entity["id"]] = page_index
        next_url = page["info"].get("next")
        if not next_url:
            break
        log(f"  {name}: page {next_url}")
        page = fetch_json(next_url)
        page_index += 1
    entities = sorted(ordered, key=lambda entity: entity["id"])
    ids = [entity["id"] for entity in entities]
    if len(set(ids)) != len(ids):
        raise ValueError(f"{name}: duplicate ids collected")
    if len(entities) != expected_count:
        raise ValueError(
            f"{name}: collected {len(entities)} entities, expected {expected_count}"
        )
    return entities, page_index_by_id


def write_json_atomic(path, entities):
    temp_path = path.with_suffix(path.suffix + ".tmp")
    try:
        with open(temp_path, "w", encoding="utf-8", newline="\n") as output:
            json.dump(entities, output, ensure_ascii=False, indent=1)
            output.write("\n")
        temp_path.rename(path)
    except OSError as error:
        with contextlib.suppress(OSError):
            temp_path.unlink(missing_ok=True)
        raise ValueError(f"cannot write {path}: {error}") from error


def write_avatar_atomic(path, data):
    temp_path = path.with_suffix(path.suffix + ".tmp")
    try:
        temp_path.write_bytes(data)
        temp_path.rename(path)
    except OSError as error:
        with contextlib.suppress(OSError):
            temp_path.unlink(missing_ok=True)
        raise ValueError(f"cannot write {path}: {error}") from error


def select_avatars(characters, page_index_by_id, avatar_pages):
    """Characters whose avatars should be bundled (first N pages, or all)."""
    if avatar_pages <= 0:
        return list(characters)
    return [
        character for character in characters
        if page_index_by_id[character["id"]] < avatar_pages
    ]


def remove_stale_avatars(avatar_dir, selected_characters):
    """Delete avatar files outside the selected set so reruns stay consistent."""
    expected_names = {f"{character['id']}.jpeg" for character in selected_characters}
    removed = 0
    for path in sorted(avatar_dir.iterdir()):
        if path.name in expected_names or not path.is_file():
            continue
        path.unlink()
        removed += 1
    if removed:
        log(f"  removed {removed} stale avatar files outside the selected set")


def validate_avatar(path):
    try:
        with open(path, "rb") as avatar_file:
            head = avatar_file.read(4)
    except OSError as error:
        raise ValueError(f"cannot read {path}: {error}") from error
    if len(head) < 2 or not has_image_signature(head):
        raise ValueError(f"{path}: not a JPEG or PNG image")


def main():
    parser = argparse.ArgumentParser(
        description="Download a Rick & Morty API snapshot into benchmark assets."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Output directory (default: assets dir next to this script's module).",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-download avatars even if they already exist.",
    )
    parser.add_argument(
        "--avatar-pages",
        type=int,
        default=DEFAULT_AVATAR_PAGES,
        help="Bundle avatars only for the first N pages of /api/character in API "
             "page order (0 = all characters). Default: %(default)s.",
    )
    args = parser.parse_args()

    output_dir = args.output_dir
    if output_dir is None:
        output_dir = Path(__file__).resolve().parent.parent / "src/main/assets/rickandmorty"
    output_dir = Path(output_dir)

    avatar_dir = output_dir / "avatars"
    avatar_dir.mkdir(parents=True, exist_ok=True)

    characters = None
    character_page_index = None
    for resource_name, file_name in RESOURCES.items():
        log(f"Downloading {resource_name}...")
        entities, page_index_by_id = download_resource(resource_name)
        write_json_atomic(output_dir / file_name, entities)
        log(f"  {file_name}: {len(entities)} entities")
        if resource_name == "character":
            characters = entities
            character_page_index = page_index_by_id

    if characters is None or character_page_index is None:
        raise ValueError("character resource was not downloaded")

    selected_characters = select_avatars(
        characters, character_page_index, args.avatar_pages
    )
    log(f"Selected {len(selected_characters)} characters for avatars "
        f"(--avatar-pages {args.avatar_pages or 'all'})")

    remove_stale_avatars(avatar_dir, selected_characters)

    downloaded = 0
    skipped = 0
    for character in selected_characters:
        avatar_path = avatar_dir / f"{character['id']}.jpeg"
        if not args.force and avatar_path.exists() and avatar_path.stat().st_size > 0:
            skipped += 1
            continue
        data = fetch_bytes(character["image"])
        if not has_image_signature(data):
            raise ValueError(
                f"{character['image']}: avatar for character {character['id']} "
                "is not a JPEG or PNG image"
            )
        write_avatar_atomic(avatar_path, data)
        downloaded += 1
        if downloaded % 50 == 0:
            log(f"  avatars: {downloaded} downloaded")
        time.sleep(AVATAR_PAUSE_SECONDS)
    log(f"Avatars: {downloaded} downloaded, {skipped} skipped")

    for character in selected_characters:
        validate_avatar(avatar_dir / f"{character['id']}.jpeg")

    log(f"Done. Snapshot in {output_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
