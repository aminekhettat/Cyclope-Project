"""Verify the immutable VisionLab voicekit copy without another checkout or network access."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
provenance = json.loads((root / "docs/voicekit-provenance.json").read_text())
expected = {item["path"]: item["sha256"] for item in provenance["files"]}
module = root / "voicekit"
actual = {
    str(path.relative_to(module)): hashlib.sha256(path.read_bytes()).hexdigest()
    for path in module.rglob("*")
    if path.is_file() and not ({"build", ".gradle"} & set(path.relative_to(module).parts))
}
assert actual == expected, "voicekit copy differs from the recorded immutable source; synchronize both hosts intentionally"
print(f"Shared voicekit: {len(actual)} files byte-identical to {provenance['source_commit']}")
