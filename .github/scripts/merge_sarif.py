import glob
import json
import sys
from pathlib import Path
from urllib.parse import unquote, urlparse
from urllib.request import url2pathname

DROPPED_RULES = {"LintBaseline"}

pattern, output = sys.argv[1], sys.argv[2]
root = Path.cwd().resolve()


def to_repo_relative(location, base_ids):
    uri = location.get("uri", "")
    base_id = location.pop("uriBaseId", None)
    if base_id in base_ids:
        uri = base_ids[base_id]["uri"].rstrip("/") + "/" + uri
    if uri.startswith("file:"):
        path = Path(url2pathname(unquote(urlparse(uri).path))).resolve()
        try:
            uri = path.relative_to(root).as_posix()
        except ValueError:
            uri = path.as_uri()
    location["uri"] = uri


def fix_locations(node, base_ids):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "artifactLocation" and isinstance(value, dict):
                to_repo_relative(value, base_ids)
            else:
                fix_locations(value, base_ids)
    elif isinstance(node, list):
        for item in node:
            fix_locations(item, base_ids)


runs = []
for path in sorted(glob.glob(pattern, recursive=True)):
    with open(path, encoding="utf-8") as f:
        runs.extend(json.load(f).get("runs", []))
if not runs:
    sys.exit(0)

rules, rule_index, results = [], {}, []
for run in runs:
    for rule in run["tool"]["driver"].get("rules", []):
        if rule["id"] not in rule_index:
            rule_index[rule["id"]] = len(rules)
            rules.append(rule)
for run in runs:
    run_rules = run["tool"]["driver"].get("rules", [])
    base_ids = run.get("originalUriBaseIds", {})
    for result in run.get("results", []):
        rule_id = result.get("ruleId")
        if rule_id is None and "ruleIndex" in result:
            rule_id = run_rules[result["ruleIndex"]]["id"]
        if rule_id in DROPPED_RULES:
            continue
        result["ruleId"] = rule_id
        if rule_id in rule_index:
            result["ruleIndex"] = rule_index[rule_id]
        else:
            result.pop("ruleIndex", None)
        fix_locations(result, base_ids)
        results.append(result)

driver = dict(runs[0]["tool"]["driver"], rules=rules)
merged = {"version": "2.1.0", "runs": [{"tool": {"driver": driver}, "results": results}]}
with open(output, "w", encoding="utf-8") as f:
    json.dump(merged, f)
print(f"Merged {len(runs)} runs into {output}: {len(results)} results, {len(rules)} rules")
