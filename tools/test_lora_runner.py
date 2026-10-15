import json
import pathlib
import tempfile
import unittest
from argparse import Namespace

from tools import lora_runner


class LoraRunnerTest(unittest.TestCase):
    def test_minimum_dataset_and_task_filter(self):
        sample = {"id": "sample", "task": "speech_clarity", "prompt": "Hallo?",
                  "expectedAnswer": "Guten Tag.", "knowledgeIds": []}
        with self.assertRaisesRegex(ValueError, "at least 20"):
            lora_runner.select_examples([sample], "speech_clarity")
        rows = [dict(sample, id=f"sample-{i}") for i in range(20)]
        self.assertEqual(20, len(lora_runner.select_examples(rows, "speech_clarity")))
        with self.assertRaisesRegex(ValueError, "found 0"):
            lora_runner.select_examples(rows, "gps_confidence")

    def test_builds_reproducible_alpaca_dataset_config_and_manifest(self):
        rows = [{"id": f"s-{i}", "task": "speech_clarity", "prompt": "Wie geht es?",
                 "expectedAnswer": "Gut.", "knowledgeIds": []} for i in range(20)]
        with tempfile.TemporaryDirectory() as temp:
            output = pathlib.Path(temp) / "model-output"
            output.mkdir()
            args = Namespace(model="Qwen/Qwen3.5-4B-Base", revision="a" * 40,
                template="qwen3_nothink", output_dir=str(output), lora_rank=16,
                gradient_accumulation=8, learning_rate=0.0001, epochs=3, seed=42,
                preset="speech_clarity")
            work = pathlib.Path(temp) / "work"
            work.mkdir()
            result = lora_runner.build_training_files(work, rows, args, "1.2.3",
                {"torch":"2.x", "cuda":"12.x", "devices":[]})
            data = json.loads((work / "reviewed-sft.json").read_text(encoding="utf-8"))
            config = (work / "train.yaml").read_text(encoding="utf-8")
            manifest = json.loads((output / "training-manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(20, len(data))
            self.assertEqual("Wie geht es?", data[0]["instruction"])
            self.assertIn('finetuning_type: "lora"', config)
            self.assertIn("model_revision:", config)
            self.assertEqual("1.2.3", manifest["trainerVersion"])
            self.assertEqual(64, len(manifest["datasetSha256"]))
            self.assertTrue(pathlib.Path(result["config"]).exists())


if __name__ == "__main__":
    unittest.main()
