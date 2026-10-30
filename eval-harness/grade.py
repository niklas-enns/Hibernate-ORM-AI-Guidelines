#!/usr/bin/env python3
"""Grade skill-evaluation runs with the hidden tests.

Usage: python3 eval-harness/grade.py <iteration-dir> [scratch-dir]

For every <case>/<config>/outputs/project: copies the project to a scratch directory, runs the run's own
`mvn test`, then adds the hidden tests (hidden-tests/common + hidden-tests/<case>) and runs them. Hidden test
methods sorted by name map 1:1 onto the case's assertions; the last assertion is always "own test suite passes".
Writes <case>/<config>/grading.json in the format the skill-creator viewer expects.
"""
import concurrent.futures
import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HARNESS = os.path.dirname(os.path.abspath(__file__))


def mvn(project, *args):
    result = subprocess.run(["mvn", "-B", "test", *args], cwd=project, capture_output=True, text=True, timeout=900)
    return result.returncode, result.stdout + result.stderr


def grade(run_dir, case, assertions, scratch):
    project = os.path.join(scratch, case + "-" + os.path.basename(run_dir))
    shutil.rmtree(project, ignore_errors=True)
    shutil.copytree(os.path.join(run_dir, "outputs", "project"), project,
                    ignore=shutil.ignore_patterns("target"))

    own_code, own_log = mvn(project)
    own_summary = [l for l in own_log.splitlines() if "Tests run:" in l and " in " not in l]
    own = {"text": assertions[-1], "passed": own_code == 0,
           "evidence": (own_summary[-1].strip() if own_summary else "") or ("BUILD SUCCESS" if own_code == 0 else
                       "mvn test failed: " + " | ".join(l for l in own_log.splitlines() if "ERROR" in l)[:300])}

    grading_dir = os.path.join(project, "src", "test", "java", "shop", "grading")
    os.makedirs(grading_dir, exist_ok=True)
    for f in glob.glob(os.path.join(HARNESS, "hidden-tests", "common", "*.java")) + \
             glob.glob(os.path.join(HARNESS, "hidden-tests", case, "*.java")):
        shutil.copy(f, grading_dir)
    shutil.rmtree(os.path.join(project, "target", "surefire-reports"), ignore_errors=True)
    code, log = mvn(project, "-Dtest=shop.grading.*GradingTest", "-Dsurefire.failIfNoSpecifiedTests=false")

    results = {}
    for report in glob.glob(os.path.join(project, "target", "surefire-reports", "TEST-shop.grading.*.xml")):
        for tc in ET.parse(report).getroot().iter("testcase"):
            problem = tc.find("failure")
            if problem is None:
                problem = tc.find("error")
            message = None if problem is None else (problem.get("message") or problem.text or "failed")
            results[tc.get("name")] = message

    expectations = []
    hidden = sorted(results)
    for i, text in enumerate(assertions[:-1]):
        if not results:
            errors = [l for l in log.splitlines() if "ERROR" in l and ".java" in l][:3]
            expectations.append({"text": text, "passed": False,
                                 "evidence": "hidden tests did not run (compile error?): " + " | ".join(errors)[:400]})
            continue
        name = hidden[i] if i < len(hidden) else None
        message = results.get(name, "missing hidden test")
        expectations.append({"text": text, "passed": message is None,
                             "evidence": ("hidden test %s passed" % name) if message is None
                             else ("hidden test %s failed: %s" % (name, " ".join(message.split())[:400]))})
    expectations.append(own)

    passed = sum(e["passed"] for e in expectations)
    grading = {"expectations": expectations,
               "summary": {"passed": passed, "failed": len(expectations) - passed, "total": len(expectations),
                           "pass_rate": round(passed / len(expectations), 2)}}
    timing = os.path.join(run_dir, "timing.json")
    if os.path.exists(timing):
        grading["timing"] = json.load(open(timing))
    json.dump(grading, open(os.path.join(run_dir, "grading.json"), "w"), indent=2)
    return "%s/%s: %d/%d" % (case, os.path.basename(run_dir), passed, len(expectations))


def main():
    iteration = os.path.abspath(sys.argv[1])
    scratch = sys.argv[2] if len(sys.argv) > 2 else tempfile.mkdtemp(prefix="hibernate-eval-")
    jobs = []
    for case in sorted(os.listdir(iteration)):
        meta = os.path.join(iteration, case, "eval_metadata.json")
        if not os.path.exists(meta):
            continue
        assertions = json.load(open(meta))["assertions"]
        for config in ("with_skill", "without_skill"):
            run_dir = os.path.join(iteration, case, config)
            if os.path.isdir(os.path.join(run_dir, "outputs", "project")):
                jobs.append((run_dir, case, assertions))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for line in pool.map(lambda j: grade(*j, scratch), jobs):
            print(line, flush=True)


if __name__ == "__main__":
    main()
