"""Prepare/publish the public catalog on an independent data branch without a checkout."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

BRANCH = "model-catalog"
REF = f"refs/heads/{BRANCH}"
CATALOG_PATH = "online-models.json"
SEED_PATH = "app/src/main/assets/online-models.json"


def git(root: Path, *args: str, data: bytes | None = None, env=None) -> bytes:
    return subprocess.run(
        ["git", "-C", str(root), *args], input=data, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=True, timeout=120, env=env,
    ).stdout


def remote_head(root: Path) -> str | None:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-remote", "--exit-code", "--heads", "origin", REF],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60,
    )
    if result.returncode == 2:
        return None
    result.check_returncode()
    fields = result.stdout.decode("ascii").strip().split()
    if len(fields) != 2 or fields[1] != REF or not re.fullmatch(r"[0-9a-f]{40,64}", fields[0]):
        raise ValueError("Unexpected catalog branch response")
    git(root, "fetch", "--no-tags", "origin", REF)
    fetched = git(root, "rev-parse", "FETCH_HEAD").decode("ascii").strip()
    if fetched != fields[0]:
        raise ValueError("Catalog branch changed while fetching; retry the workflow")
    return fetched


def decode_catalog(data: bytes):
    # Share the collector's strict decoder; never let the publication path bypass validation.
    from update_model_catalog import decode_catalog as decode
    return decode(data)


def collection_baseline(bundled: bytes, published: bytes | None) -> bytes:
    seed = decode_catalog(bundled)
    if published is None:
        return bundled
    remote = decode_catalog(published)
    if seed["revision"] == remote["revision"] and seed != remote:
        raise ValueError("Bundled and published catalogs conflict at the same revision")
    return bundled if seed["revision"] > remote["revision"] else published


def prepare(root: Path, directory: Path) -> None:
    head = remote_head(root)
    published = git(root, "show", f"{head}:{CATALOG_PATH}") if head else None
    previous = collection_baseline((root / SEED_PATH).read_bytes(), published)
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "previous.json").write_bytes(previous)
    if published is not None:
        (directory / "published.json").write_bytes(published)
    (directory / "publication.json").write_text(
        json.dumps({"previousCommit": head}) + "\n", encoding="utf-8",
    )


def publish(root: Path, directory: Path) -> bool:
    metadata = json.loads((directory / "publication.json").read_text(encoding="utf-8"))
    if set(metadata) != {"previousCommit"}:
        raise ValueError("Invalid publication metadata")
    expected = metadata["previousCommit"]
    if expected is not None and (not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{40,64}", expected)):
        raise ValueError("Invalid previous commit")
    candidate_bytes = (directory / CATALOG_PATH).read_bytes()
    candidate = decode_catalog(candidate_bytes)
    previous_bytes = (directory / "previous.json").read_bytes()
    previous = decode_catalog(previous_bytes)
    changed = candidate["providers"] != previous["providers"]
    if changed:
        if candidate["revision"] != previous["revision"] + 1:
            raise ValueError("Changed catalog must increment revision exactly once")
        if candidate["updatedAtEpochMillis"] < previous["updatedAtEpochMillis"]:
            raise ValueError("Catalog update time must not go backwards")
    elif candidate != previous:
        raise ValueError("Unchanged catalog must preserve metadata")

    current = remote_head(root)
    if current != expected:
        raise ValueError("Catalog branch changed since collection; retry the workflow")
    if current:
        published = git(root, "show", f"{current}:{CATALOG_PATH}")
        if published != (directory / "published.json").read_bytes():
            raise ValueError("Previous document does not match the published catalog")
        if previous_bytes != collection_baseline((root / SEED_PATH).read_bytes(), published):
            raise ValueError("Collection baseline differs from the bundled/published catalogs")
        remote = decode_catalog(published)
        if candidate == remote:
            return False
        if candidate["revision"] <= remote["revision"]:
            raise ValueError("Publication must advance the remote catalog revision")
    elif previous_bytes != collection_baseline((root / SEED_PATH).read_bytes(), None):
        raise ValueError("Initial collection baseline differs from the bundled catalog")

    # A separate index builds only the data tree. No branch switch, worktree, or force push.
    with tempfile.TemporaryDirectory(prefix="stone-catalog-index-") as temporary:
        environment = os.environ.copy()
        environment.update({
            "GIT_INDEX_FILE": str(Path(temporary) / "index"),
            "GIT_AUTHOR_NAME": "github-actions[bot]",
            "GIT_AUTHOR_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
            "GIT_COMMITTER_NAME": "github-actions[bot]",
            "GIT_COMMITTER_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
        })
        if current:
            git(root, "read-tree", current, env=environment)
        else:
            git(root, "read-tree", "--empty", env=environment)
        blob = git(root, "hash-object", "-w", "--stdin", data=candidate_bytes).decode("ascii").strip()
        git(root, "update-index", "--add", "--cacheinfo", f"100644,{blob},{CATALOG_PATH}", env=environment)
        tree = git(root, "write-tree", env=environment).decode("ascii").strip()
        parents = ["-p", current] if current else []
        commit = git(
            root, "commit-tree", tree, *parents, "-m",
            f"chore(models): publish catalog revision {candidate['revision']}", env=environment,
        ).decode("ascii").strip()
        git(root, "push", "origin", f"{commit}:{REF}")
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--prepare", action="store_true")
    mode.add_argument("--publish", action="store_true")
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    args = parser.parse_args()
    if args.prepare:
        prepare(args.repository, args.directory)
        print("Prepared previous catalog")
    else:
        print("Catalog published" if publish(args.repository, args.directory) else "Catalog unchanged")


if __name__ == "__main__":
    main()
