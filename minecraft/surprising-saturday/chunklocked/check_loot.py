"""Check the copied Chunklock 2.1.0 tables before packaging the mod."""

import json
from pathlib import Path

root = Path(__file__).parent / "src/main/resources/chunklocked/loot"
tables = {file.relative_to(root).with_suffix("").as_posix(): json.loads(file.read_text())
          for file in root.rglob("*.json")}
assert len(tables) == 151, f"Expected 151 source tables, found {len(tables)}"

for name, table in tables.items():
    pools = table["pools"]
    assert len(pools) == 1 and pools[0]["rolls"] == 1, name
    for entry in pools[0]["entries"]:
        assert entry["weight"] > 0, name
        if entry["type"].endswith("loot_table"):
            target = entry["value"]
            assert target == "minecraft:blocks/gravel" or target.removeprefix("chunklock:") in tables, (name, target)
        else:
            assert entry["type"].endswith("item") and entry["name"].startswith("minecraft:"), name
        for modifier in entry.get("functions", []):
            assert modifier["function"].endswith("set_count"), name
            count = modifier["count"]
            if isinstance(count, dict):
                assert 0 < count["min"] <= count["max"], name
            else:
                assert count > 0, name

print(f"Checked {len(tables)} Chunklock loot tables")
