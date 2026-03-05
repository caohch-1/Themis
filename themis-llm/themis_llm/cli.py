import argparse
import json
import os
from pathlib import Path

from .claude_client import ClaudeClient
from .models import ViolationBundle
from .prompts import PromptLoader
from .workflow import GenerationWorkflow, ParameterSeedGenerator, RepairWorkflow, write_artifacts


def main() -> None:
    parser = argparse.ArgumentParser(prog="themis-llm")
    parser.add_argument("--bundle", required=True)
    parser.add_argument("--adjacency", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--prompts", default="config/prompts")
    parser.add_argument("--model", default="claude-4")
    parser.add_argument("--api-key-env", default="ANTHROPIC_API_KEY")
    parser.add_argument("--repair-threshold", type=int, default=8)
    parser.add_argument("--restart-cycles", type=int, default=5)
    parser.add_argument("--compile-classpath", default="")
    parser.add_argument("--runtime-classpath", default="")
    parser.add_argument("--build-oracle", default="")
    args = parser.parse_args()

    bundle_data = json.loads(Path(args.bundle).read_text(encoding="utf-8"))
    adjacency = json.loads(Path(args.adjacency).read_text(encoding="utf-8"))

    bundles = bundle_data if isinstance(bundle_data, list) else [bundle_data]
    bundle_objs = [ViolationBundle(**item) for item in bundles]

    prompt_root = Path(args.prompts)
    if not prompt_root.exists():
        alt = Path("../config/prompts")
        if alt.exists():
            prompt_root = alt
    loader = PromptLoader(prompt_root)
    client = ClaudeClient(model=args.model, api_key=os.getenv(args.api_key_env, ""))
    repair = RepairWorkflow(client, loader)
    workflow = GenerationWorkflow(
        client,
        loader,
        repair,
        ParameterSeedGenerator(),
        repair_threshold=args.repair_threshold,
        restart_cycles=args.restart_cycles,
        compile_classpath=args.compile_classpath,
        runtime_classpath=args.runtime_classpath,
        build_oracle=args.build_oracle,
    )

    artifacts = []
    for bundle in bundle_objs:
        artifacts.append(workflow.run(bundle, adjacency))
    write_artifacts(Path(args.output), artifacts)


if __name__ == "__main__":
    main()
