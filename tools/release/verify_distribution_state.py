#!/usr/bin/env python3
import argparse, json
from pathlib import Path

ALLOWED={
 "test-signed-installable-rc": (False,False),
 "production-signed": (True,False),
 "store-published": (True,True),
}
p=argparse.ArgumentParser()
p.add_argument("--distribution-state",required=True)
p.add_argument("--require-production",action="store_true")
a=p.parse_args()
d=json.loads(Path(a.distribution_state).read_text())
state=d.get("androidArtifact")
if state not in ALLOWED:
 raise SystemExit("invalid distribution state")
expected=ALLOWED[state]
actual=(d.get("productionSigned"),d.get("storePublished"))
if actual != expected:
 raise SystemExit("inconsistent distribution state")
if a.require_production and actual != (True,True):
 raise SystemExit("production distribution required")
print(json.dumps({"distributionStateValid":True,"androidArtifact":state},sort_keys=True))
