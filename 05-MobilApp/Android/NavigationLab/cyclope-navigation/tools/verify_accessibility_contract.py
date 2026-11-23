"""Static native-UI contract checks; these do not replace TalkBack tests on a phone."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
a = "{http://schemas.android.com/apk/res/android}"
layout = ET.parse(root / "app/src/main/res/layout/activity_main.xml").getroot()
by_id = {node.get(a + "id", "").split("/")[-1]: node for node in layout.iter()}
interactive = [node for node in layout.iter() if node.tag in {"Button", "EditText"}]
for node in interactive:
    assert node.get(a + "contentDescription", "").startswith("@string/"), node.attrib
    assert int(node.get(a + "minHeight", "0dp").removesuffix("dp")) >= 48, node.attrib
    assert int(node.get(a + "minWidth", "0dp").removesuffix("dp")) >= 48, node.attrib
    assert node.get(a + "importantForAccessibility") == "yes", node.attrib
for name in ["tvStatus", "tvAnnouncements"]:
    assert by_id[name].get(a + "accessibilityLiveRegion") == "polite"
for name in ["tvSources", "tvNav"]:
    assert by_id[name].get(a + "accessibilityLiveRegion") == "none", "Unbounded logs must not spam TalkBack"
assert next(iter(layout[0])).get(a + "id") == "@+id/btnSkipNavigation"
activity = (root / "app/src/main/java/org/blindsystems/cyclope/navlab/MainActivity.kt").read_text()
assert "etAddress.requestFocus()" in activity and "ACTION_ACCESSIBILITY_FOCUS" in activity
assert "VoiceAccessibility.announceRequested" in activity
assert "android.speech.tts" not in (root / "app/src/main/java/org/blindsystems/cyclope/navlab/AndroidVoiceOutput.kt").read_text()
print(f"Native UI: {len(interactive)} labeled interactive targets >=48dp; polite regions and skip-link wiring verified statically")
