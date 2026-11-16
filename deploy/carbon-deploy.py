#!/usr/bin/env python3
"""Plan and trigger one-use CATS deployments through GitHub Actions artifacts."""

import io
import json
import math
import os
import re
import sys
import zipfile
from datetime import datetime, time, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen
from zoneinfo import ZoneInfo

UTC = timezone.utc
UK = ZoneInfo("Europe/London")
FORECAST = "https://api.energypriceforecast.eu/api/v1/evcc/tariff?country=de&type=co2&hours=48"
REPO = os.environ.get("GITHUB_REPOSITORY")
TOKEN = os.environ.get("GITHUB_TOKEN")


def choose(rows, now):
    from cats.forecast import PointEstimate, WindowedForecast

    deadline = datetime.combine(now.astimezone(UK).date() + timedelta(days=1), time(7), UK).astimezone(UTC)
    hourly = []
    previous_end = None
    for row in rows:
        start = datetime.fromisoformat(row["start"].replace("Z", "+00:00")).astimezone(UTC)
        end = datetime.fromisoformat(row["end"].replace("Z", "+00:00")).astimezone(UTC)
        value = float(row["value"])
        if end - start != timedelta(hours=1) or (previous_end and start != previous_end) or not math.isfinite(value) or value < 0:
            raise ValueError("Invalid or incomplete CO2 forecast")
        hourly.append(PointEstimate(value, start))
        previous_end = end
    if len(hourly) < 2 or hourly[0].datetime > now or previous_end < deadline:
        raise ValueError("CO2 forecast does not cover the deadline")
    hourly.append(PointEstimate(hourly[-1].value, previous_end))
    points = [PointEstimate(a.value + (b.value - a.value) * i / 12, a.datetime + timedelta(minutes=5 * i))
              for a, b in zip(hourly, hourly[1:]) for i in range(12)] + [hourly[-1]]
    windows = WindowedForecast(points, 15, start=now, end_constraint=deadline)
    best = min(window for window in windows if window.start <= deadline - timedelta(minutes=30))
    return best.start, deadline, windows[0].value, best.value


def github(path, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = Request("https://api.github.com" + path, data=data, method=method,
                      headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json",
                               "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28"})
    with urlopen(request, timeout=30) as response:
        result = response.read()
    return json.loads(result) if result else None


def artifacts(target=None):
    for selected in ([target] if target else ["dev", "production"]):
        page = 1
        while True:
            found = github(f"/repos/{REPO}/actions/artifacts?name=carbon-deploy-{selected}&per_page=100&page={page}")["artifacts"]
            yield from (item for item in found if not item["expired"])
            if len(found) < 100:
                break
            page += 1


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        return None


def load(artifact):
    path = f"/repos/{REPO}/actions/artifacts/{artifact['id']}/zip"
    request = Request("https://api.github.com" + path,
                      headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json"})
    try:
        build_opener(NoRedirect).open(request, timeout=30)
    except HTTPError as error:
        if error.code != 302:
            raise
        with urlopen(error.headers["Location"], timeout=30) as response:
            archive = response.read()
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        plan = json.loads(zipped.read("plan.json"))
    run = github(f"/repos/{REPO}/actions/runs/{plan['run_id']}")
    if run["conclusion"] is None:
        return None
    assert plan["repository"] == REPO and artifact["workflow_run"]["id"] == plan["run_id"]
    assert artifact["name"] == f"carbon-deploy-{plan['target']}"
    assert run["event"] == "workflow_dispatch" and run["conclusion"] == "success"
    assert run["path"].split("@")[0].endswith("/deploy.yml") and run["actor"]["login"] == plan["requester"]
    assert plan["target"] in ("dev", "production") and (plan["target"] != "production" or plan["requester"] == "FreddyMSchubert")
    assert re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", plan["version"])
    assert re.fullmatch(r"[A-Za-z0-9._/-]{0,200}", plan["source_ref"]) and ".." not in plan["source_ref"]
    assert isinstance(plan["warning_minutes"], int) and 0 <= plan["warning_minutes"] <= 60
    assert isinstance(plan["force"], bool)
    return plan


def delete(artifact):
    github(f"/repos/{REPO}/actions/artifacts/{artifact['id']}", method="DELETE")


def plan():
    target, version = os.environ["PLAN_TARGET"], os.environ["PLAN_VERSION"]
    source_ref = os.environ.get("PLAN_SOURCE_REF", "")
    warning = int(os.environ["PLAN_WARNING_MINUTES"])
    assert target in ("dev", "production") and (target != "production" or version.lower() != "dev")
    assert re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", version)
    assert re.fullmatch(r"[A-Za-z0-9._/-]{0,200}", source_ref) and ".." not in source_ref
    assert 0 <= warning <= 60
    with urlopen(Request(FORECAST, headers={"User-Agent": "MMUCraft-CATS-deploy/1"}), timeout=20) as response:
        rows = json.load(response)
    start, deadline, current, best = choose(rows, datetime.now(UTC))
    payload = dict(repository=REPO, run_id=int(os.environ["GITHUB_RUN_ID"]), requester=os.environ["GITHUB_ACTOR"],
                   target=target, version=version, source_ref=source_ref, warning_minutes=warning,
                   force=os.environ["PLAN_FORCE"] == "true", execute_at=start.isoformat(), deadline=deadline.isoformat())
    Path(os.environ["PLAN_OUTPUT"]).write_text(json.dumps(payload))
    line = (f"CATS chose {start.astimezone(UK):%Y-%m-%d %H:%M %Z} for {target} / {version}; "
            f"forecast {current:.1f} now, {best:.1f} gCO2e/kWh then. "
            f"[Germany forecast](https://stroomprijsprognose.nl/en/evcc-electricity-price-co2-forecast/).")
    print(line)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        summary.write(line + "\n")


def dispatch():
    runs = github(f"/repos/{REPO}/actions/workflows/deploy.yml/runs?event=repository_dispatch&per_page=100")["workflow_runs"]
    now = datetime.now(UTC)
    for artifact in artifacts():
        plan_data = load(artifact)
        if plan_data is None:
            continue
        deadline = datetime.fromisoformat(plan_data["deadline"])
        if now >= deadline:
            print(f"Missed 07:00 UK deadline for {artifact['name']}")
            delete(artifact)
        elif now >= datetime.fromisoformat(plan_data["execute_at"]):
            if any(run["display_title"].startswith(f"Deploy planned #{artifact['id']} ") for run in runs):
                continue
            github(f"/repos/{REPO}/dispatches", method="POST",
                   body={"event_type": "carbon-aware-deploy", "client_payload": {"artifact_id": artifact["id"], "target": plan_data["target"]}})
            print(f"Dispatched {artifact['name']} ({artifact['id']})")


def claim(artifact_id, target):
    assert re.fullmatch(r"[1-9][0-9]*", artifact_id) and target in ("dev", "production")
    artifact = next((item for item in artifacts(target) if str(item["id"]) == artifact_id), None)
    if artifact is None:
        print("Plan already claimed or cancelled")
        return
    data = load(artifact)
    assert data is not None
    assert data["target"] == target
    now = datetime.now(UTC)
    assert now >= datetime.fromisoformat(data["execute_at"])
    if now >= datetime.fromisoformat(data["deadline"]):
        delete(artifact)
        print("Plan missed the 07:00 UK deadline")
        return
    delete(artifact)
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key in ("target", "version", "source_ref", "warning_minutes", "force"):
            output.write(f"{key}={str(data[key]).lower() if isinstance(data[key], bool) else data[key]}\n")
        output.write("claimed=true\n")


def self_test():
    now = datetime(2026, 3, 28, 12, 30, tzinfo=UTC)
    rows = [{"start": (now + timedelta(hours=i)).isoformat(),
             "end": (now + timedelta(hours=i + 1)).isoformat(), "value": 50 if i == 17 else 300}
            for i in range(48)]
    start, deadline, current, best = choose(rows, now)
    assert deadline == datetime(2026, 3, 29, 6, tzinfo=UTC)
    assert now <= start <= deadline - timedelta(minutes=30) and best < current
    print("CATS planner and UK daylight-saving deadline passed")


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    if command == "test":
        self_test()
    elif command == "plan":
        plan()
    elif command == "dispatch":
        dispatch()
    elif command == "claim":
        claim(*args)
    elif command == "cancel":
        assert args[0] in ("dev", "production")
        for pending in artifacts(args[0]):
            delete(pending)
    else:
        raise ValueError(f"Unknown command: {command}")
