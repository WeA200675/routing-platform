#!/usr/bin/env python3
"""Small companion app for curating/exporting verified local-assistant examples.

This prepares reproducible data for a separately pinned fine-tuner. It does not
claim to modify model weights. Traffic examples retain their approved KB IDs.
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import tempfile
import urllib.error
import urllib.request


def fetch_dataset(base_url, token):
    if not base_url.startswith("https://") and not base_url.startswith("http://127.0.0.1"):
        raise ValueError("training hub must use HTTPS (HTTP is allowed only on loopback)")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/v1/training/dataset",
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError("training hub request failed")
        value = json.load(response)
    examples = value.get("examples")
    if not isinstance(examples, list):
        raise ValueError("hub returned an invalid training dataset")
    return examples


def validate_examples(examples):
    seen = set()
    safe = []
    for item in examples:
        if not isinstance(item, dict):
            raise ValueError("dataset contains a non-object example")
        required = ("id", "task", "prompt", "expectedAnswer", "knowledgeIds")
        if any(key not in item for key in required):
            raise ValueError("dataset example is missing required fields")
        if item["id"] in seen:
            raise ValueError("dataset contains duplicate example ids")
        if item["task"] in {"traffic_summary", "hazard_summary", "route_explanation"} and not item["knowledgeIds"]:
            raise ValueError("traffic/navigation examples must cite approved knowledge")
        if len(item["prompt"]) > 2_000 or len(item["expectedAnswer"]) > 2_000:
            raise ValueError("example exceeds text limits")
        # The app never stores raw GPS traces or trip coordinates in training rows.
        combined = item["prompt"] + " " + item["expectedAnswer"]
        if _looks_like_coordinate(combined):
            raise ValueError("example contains coordinate-shaped data; redact it before training")
        seen.add(item["id"])
        safe.append(item)
    return safe


def _looks_like_coordinate(text):
    import re
    return bool(re.search(r"(?<!\d)(?:[+-]?\d{1,2}\.\d{4,})\s*[,; ]\s*[+-]?\d{1,3}\.\d{4,}(?!\d)", text))


def export_jsonl(examples, destination):
    checked = validate_examples(examples)
    destination = pathlib.Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=destination.name + ".", dir=destination.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            for row in checked:
                stream.write(json.dumps({
                    "messages": [
                        {"role":"system","content":"Give concise, clear German. Never invent route, traffic, hazard, or safety facts."},
                        {"role":"user","content":row["prompt"]},
                        {"role":"assistant","content":row["expectedAnswer"]},
                    ],
                    "metadata":{"id":row["id"],"task":row["task"],"knowledgeIds":row["knowledgeIds"]},
                }, ensure_ascii=False) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return len(checked)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hub", default=os.environ.get("INTELLIGENCE_HUB_URL", ""))
    parser.add_argument("--token", default=os.environ.get("HUB_API_TOKEN", ""))
    parser.add_argument("--output", default="training-data/verified-examples.jsonl")
    args = parser.parse_args()
    if not args.hub or not args.token:
        parser.error("set --hub/INTELLIGENCE_HUB_URL and --token/HUB_API_TOKEN")
    count = export_jsonl(fetch_dataset(args.hub, args.token), args.output)
    print(f"Exported {count} validated examples to {args.output}; model weights were not changed.")


if __name__ == "__main__":
    main()
