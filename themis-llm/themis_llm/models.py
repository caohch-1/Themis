from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class ViolationBundle:
    violation_id: str
    public_api_names: List[str]
    target_statements: List[str]
    call_chain: List[str]
    source_codes: List[str]
    rpc_server_code: str
    test_suite_snippets: List[str] = field(default_factory=list)


@dataclass
class TestArtifact:
    violation_id: str
    test_name: str
    code: str
    executable: bool
    parameters: Dict[str, str] = field(default_factory=dict)
    candidate_parameters: List[Dict[str, str]] = field(default_factory=list)


@dataclass
class GenerationAttempt:
    prompt: str
    response: str
    error: str
    repaired: bool
