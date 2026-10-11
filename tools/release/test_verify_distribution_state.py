#!/usr/bin/env python3
import json, subprocess, sys, tempfile
from pathlib import Path
tool=Path(__file__).with_name("verify_distribution_state.py")
with tempfile.TemporaryDirectory() as td:
 p=Path(td)/"d.json"
 def run(v,*extra):
  p.write_text(json.dumps(v))
  return subprocess.run([sys.executable,str(tool),"--distribution-state",str(p),*extra]).returncode
 assert run({"androidArtifact":"test-signed-installable-rc","productionSigned":False,"storePublished":False})==0
 assert run({"androidArtifact":"production-signed","productionSigned":True,"storePublished":False})==0
 assert run({"androidArtifact":"store-published","productionSigned":True,"storePublished":True},"--require-production")==0
 assert run({"androidArtifact":"test-signed-installable-rc","productionSigned":True,"storePublished":False})!=0
 assert run({"androidArtifact":"store-published","productionSigned":False,"storePublished":True})!=0
 assert run({"androidArtifact":"unknown","productionSigned":False,"storePublished":False})!=0
 assert run({"androidArtifact":"test-signed-installable-rc","productionSigned":False,"storePublished":False},"--require-production")!=0
