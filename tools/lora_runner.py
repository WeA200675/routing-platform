#!/usr/bin/env python3
"""Run a real, reproducible LoRA SFT job from reviewed Route Intelligence examples.

No weights are changed during --preflight. A run requires an explicit immutable
model revision and an exactly pinned installed LLaMA-Factory version.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import math
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request


DEFAULT_SYSTEM = (
    "Antworte klar und knapp auf Deutsch. Erfinde niemals Navigations-, "
    "Verkehrs- oder Sicherheitsfakten. Bei Navigation haben verifizierte "
    "Routen- und Verkehrsdaten Vorrang; bei Unsicherheit sage das ausdrücklich."
)


def fetch_dataset(base_url: str, token: str) -> list[dict]:
    if not base_url.startswith("https://") and not base_url.startswith("http://127.0.0.1"):
        raise ValueError("Training Hub must use HTTPS (HTTP is allowed only on loopback)")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/v1/training/dataset",
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError("Training Hub dataset request failed")
        payload = json.load(response)
    rows = payload.get("examples")
    if not isinstance(rows, list):
        raise ValueError("Training Hub returned an invalid dataset")
    from tools.ai_training_app import validate_examples
    return validate_examples(rows)


def select_examples(examples: list[dict], preset: str) -> list[dict]:
    selected = [row for row in examples if row["task"] == preset]
    if len(selected) < 20:
        raise ValueError(f"LoRA requires at least 20 reviewed examples for {preset}; found {len(selected)}")
    return selected


def check_environment(expected_trainer_version: str) -> tuple[str, dict]:
    if not expected_trainer_version:
        raise ValueError("set --trainer-version to the exact validated LLaMA-Factory version")
    actual = importlib.metadata.version("llamafactory")
    if actual != expected_trainer_version:
        raise ValueError(f"LLaMA-Factory version mismatch: expected {expected_trainer_version}, found {actual}")
    executable = shutil.which("llamafactory-cli")
    if not executable:
        raise ValueError("llamafactory-cli is not installed")
    try:
        import torch
    except ImportError as error:
        raise ValueError("PyTorch is not installed in the training environment") from error
    if not torch.cuda.is_available():
        raise ValueError("CUDA GPU is unavailable; no training was started")
    devices = []
    for index in range(torch.cuda.device_count()):
        props = torch.cuda.get_device_properties(index)
        free, total = torch.cuda.mem_get_info(index)
        devices.append({
            "index": index, "name": props.name,
            "totalMemoryBytes": int(total), "freeMemoryBytes": int(free),
            "bf16": bool(torch.cuda.is_bf16_supported()),
        })
    if not devices:
        raise ValueError("no CUDA training device was found")
    return actual, {"torch": torch.__version__, "cuda": torch.version.cuda, "devices": devices}



def calculate_training_plan(example_count: int, hardware: dict, epochs: float,
                            gradient_accumulation: int) -> dict:
    """Return exact configuration arithmetic and measured capacity, never a guessed ETA."""
    devices = hardware.get("devices") or []
    if not devices:
        raise ValueError("training plan requires at least one measured CUDA device")
    device_count = len(devices)
    effective_batch = device_count * gradient_accumulation
    steps_per_epoch = math.ceil(example_count / effective_batch)
    total_steps = math.ceil(steps_per_epoch * epochs)
    return {
        "reviewedExamples": example_count,
        "epochs": epochs,
        "cudaDeviceCount": device_count,
        "gradientAccumulation": gradient_accumulation,
        "effectiveBatchSize": effective_batch,
        "estimatedOptimizerSteps": total_steps,
        "gpuMemoryTotalBytes": sum(d["totalMemoryBytes"] for d in devices),
        "gpuMemoryFreeBytesAtPreflight": sum(d["freeMemoryBytes"] for d in devices),
        "estimatedWallClockSeconds": None,
        "etaReason": "no measured baseline run for this model, GPU, and dataset",
    }


def build_training_files(workdir: pathlib.Path, examples: list[dict], args, trainer_version: str,
                         hardware: dict) -> dict:
    data = []
    for row in examples:
        data.append({
            "instruction": row["prompt"],
            "input": "",
            "output": row["expectedAnswer"],
            "system": DEFAULT_SYSTEM,
        })
    dataset_path = workdir / "reviewed-sft.json"
    dataset_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (workdir / "dataset_info.json").write_text(json.dumps({
        "route_reviewed_sft": {
            "file_name": dataset_path.name,
            "columns": {
                "prompt": "instruction", "query": "input",
                "response": "output", "system": "system",
            },
        },
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    config = {
        "model_name_or_path": args.model,
        "model_revision": args.revision,
        "trust_remote_code": False,
        "stage": "sft",
        "do_train": True,
        "finetuning_type": "lora",
        "lora_rank": args.lora_rank,
        "lora_target": "all",
        "dataset": "route_reviewed_sft",
        "dataset_dir": str(workdir),
        "template": args.template,
        "cutoff_len": 1024,
        "max_samples": len(data),
        "preprocessing_num_workers": 1,
        "dataloader_num_workers": 0,
        "output_dir": str(pathlib.Path(args.output_dir).resolve()),
        "logging_steps": 1,
        "save_steps": max(1, len(data)),
        "save_total_limit": 2,
        "overwrite_output_dir": False,
        "save_only_model": False,
        "report_to": "none",
        "per_device_train_batch_size": 1,
        "gradient_accumulation_steps": args.gradient_accumulation,
        "learning_rate": args.learning_rate,
        "num_train_epochs": args.epochs,
        "lr_scheduler_type": "cosine",
        "warmup_ratio": 0.03,
        "weight_decay": 0.01,
        "seed": args.seed,
        "plot_loss": True,
        "val_size": 0.1,
        "eval_strategy": "epoch",
        "per_device_eval_batch_size": 1,
    }
    config_path = workdir / "train.yaml"
    lines = []
    for key, value in config.items():
        if isinstance(value, bool):
            rendered = "true" if value else "false"
        elif isinstance(value, (int, float)):
            rendered = str(value)
        else:
            rendered = json.dumps(str(value), ensure_ascii=False)
        lines.append(f"{key}: {rendered}")
    config_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    digest = hashlib.sha256(dataset_path.read_bytes()).hexdigest()
    plan = calculate_training_plan(len(data), hardware, args.epochs, args.gradient_accumulation)
    manifest = {
        "createdAtEpochMillis": int(time.time() * 1000),
        "model": args.model,
        "modelRevision": args.revision,
        "trainer": "LLaMA-Factory",
        "trainerVersion": trainer_version,
        "template": args.template,
        "preset": args.preset,
        "exampleCount": len(data),
        "datasetSha256": digest,
        "hardware": hardware,
        "trainingMode": "LoRA SFT",
        "computePlan": plan,
    }
    (pathlib.Path(args.output_dir).resolve() / "training-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"config": str(config_path), "dataset": str(dataset_path), "manifest": manifest}


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hub", default=os.environ.get("INTELLIGENCE_HUB_URL", ""))
    parser.add_argument("--token", default=os.environ.get("HUB_API_TOKEN", ""))
    parser.add_argument("--trainer-version", default=os.environ.get("TRAINER_EXPECTED_VERSION", ""))
    parser.add_argument("--model", default="Qwen/Qwen3.5-4B-Base")
    parser.add_argument("--revision", default=os.environ.get("MODEL_REVISION", ""))
    parser.add_argument("--template", default="qwen3_nothink")
    parser.add_argument("--preset", default="speech_clarity",
                        choices=("speech_clarity", "route_explanation", "traffic_summary",
                                 "hazard_summary", "gps_confidence"))
    parser.add_argument("--output-dir", default="lora-output/adapter")
    parser.add_argument("--lora-rank", type=int, default=16)
    parser.add_argument("--gradient-accumulation", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=0.0001)
    parser.add_argument("--epochs", type=float, default=3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--preflight", action="store_true",
                        help="validate data/hardware and write a manifest, without changing model weights")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    if not args.hub or not args.token:
        raise SystemExit("set --hub/INTELLIGENCE_HUB_URL and --token/HUB_API_TOKEN")
    if not re.fullmatch(r"[0-9a-fA-F]{40,64}", args.revision):
        raise SystemExit("--revision must be an immutable 40-64 character model commit hash")
    if not re.fullmatch(r"[A-Za-z0-9._/-]{3,160}", args.model) or ".." in args.model.split("/"):
        raise SystemExit("--model must be a safe model repository/path")
    if not 1 <= args.lora_rank <= 128 or not 1 <= args.gradient_accumulation <= 128:
        raise SystemExit("LoRA rank or gradient accumulation is outside the supported range")
    if not 0.000001 <= args.learning_rate <= 0.01 or not 0.1 <= args.epochs <= 20:
        raise SystemExit("learning rate or epoch count is outside the supported range")
    expected = pathlib.Path(args.output_dir).expanduser().resolve()
    if expected.exists() and any(expected.iterdir()):
        raise SystemExit("output directory is not empty; choose a new output directory")
    args.output_dir = str(expected)
    try:
        examples = select_examples(fetch_dataset(args.hub, args.token), args.preset)
        trainer_version, hardware = check_environment(args.trainer_version)
    except Exception as error:
        raise SystemExit("Preflight failed; no training was started: " + str(error)) from error
    expected.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="routing-lora-") as temp:
        artifacts = build_training_files(pathlib.Path(temp), examples, args, trainer_version, hardware)
        print(json.dumps({
            "status": "preflight_passed" if args.preflight else "training_started",
            "examples": len(examples),
            "model": args.model,
            "revision": args.revision,
            "devices": hardware["devices"],
            "manifest": artifacts["manifest"],
            "weightsChanged": False if args.preflight else "trainer_process_running",
        }, ensure_ascii=False, indent=2))
        if args.preflight:
            return 0
        log_path = expected / "trainer.log"
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.run(
                [shutil.which("llamafactory-cli"), "train", artifacts["config"]],
                cwd=temp, stdout=log, stderr=subprocess.STDOUT, check=False,
            )
        if process.returncode != 0:
            raise SystemExit(f"LoRA training failed with exit code {process.returncode}; see {log_path}")
    print("LoRA training finished. Adapter and manifest are stored in " + str(expected))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
