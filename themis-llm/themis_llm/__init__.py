__all__ = ["ClaudeClient", "GenerationWorkflow", "RepairWorkflow", "ParameterSeedGenerator"]


def __getattr__(name):
    if name == "ClaudeClient":
        from .claude_client import ClaudeClient
        return ClaudeClient
    if name in {"GenerationWorkflow", "RepairWorkflow", "ParameterSeedGenerator"}:
        from .workflow import GenerationWorkflow, RepairWorkflow, ParameterSeedGenerator
        return {
            "GenerationWorkflow": GenerationWorkflow,
            "RepairWorkflow": RepairWorkflow,
            "ParameterSeedGenerator": ParameterSeedGenerator,
        }[name]
    raise AttributeError(name)
