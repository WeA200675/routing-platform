#!/usr/bin/env python3
import json, subprocess, sys, tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FIXTURES=ROOT/"platform/shared/parity/navigation-admission-fixtures.csv"

def fail(msg):
    raise SystemExit(msg)

if not FIXTURES.exists():
    fail("missing shared parity fixture corpus")
lines=[x.strip() for x in FIXTURES.read_text().splitlines() if x.strip() and not x.startswith("#")]
if not lines:
    fail("empty shared parity fixture corpus")
for line in lines:
    parts=line.split(",")
    if parts[0]=="capability" and len(parts)!=5: fail("invalid capability fixture: "+line)
    if parts[0]=="observation" and len(parts)!=6: fail("invalid observation fixture: "+line)
    if parts[0] not in {"capability","observation"}: fail("unknown fixture type: "+line)
print(json.dumps({"fixtureCount":len(lines),"corpus":str(FIXTURES.relative_to(ROOT))},sort_keys=True))
