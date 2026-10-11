#!/usr/bin/env python3
import json, subprocess, sys, tempfile
from pathlib import Path

root=Path(__file__).resolve().parent
tool=root/"verify_ga_promotion.py"
sha="a"*40
apk="b"*64
ios_digest="c"*64

with tempfile.TemporaryDirectory() as td:
    d=Path(td)
    manifest={"candidateSourceSha":sha,"releaseApkSha256":apk,"iosArtifactSha256":ios_digest}
    (d/"manifest.json").write_text(json.dumps(manifest))
    def dist(signed=True,published=True):
        (d/"dist.json").write_text(json.dumps({"androidArtifact":"production-signed","productionSigned":signed,"storePublished":published}))
    def record(platform,digest,result="pass"):
        return {"candidateSourceSha":sha,"artifactSha256":digest,"platform":platform,
                "platformVersion":"test-version","physicalDevice":True,"result":result}
    (d/"android.json").write_text(json.dumps(record("android",apk)))
    (d/"ios.json").write_text(json.dumps(record("ios",ios_digest)))
    dist()
    base=[sys.executable,str(tool),"--manifest",str(d/"manifest.json"),
          "--distribution-state",str(d/"dist.json"),"--android-device",str(d/"android.json"),
          "--ios-device",str(d/"ios.json")]
    assert subprocess.run(base).returncode==0
    dist(False,False); assert subprocess.run(base).returncode!=0
    dist(True,False); assert subprocess.run(base).returncode!=0
    dist()
    bad=dict(manifest); bad.pop("iosArtifactSha256")
    (d/"manifest.json").write_text(json.dumps(bad)); assert subprocess.run(base).returncode!=0
    (d/"manifest.json").write_text(json.dumps(manifest))
    (d/"android.json").write_text(json.dumps(record("android","d"*64))); assert subprocess.run(base).returncode!=0
    (d/"android.json").write_text(json.dumps(record("android",apk,"fail"))); assert subprocess.run(base).returncode!=0
    (d/"android.json").write_text(json.dumps(record("ios",apk))); assert subprocess.run(base).returncode!=0
    (d/"android.json").write_text(json.dumps(record("android",apk)))
    (d/"ios.json").write_text(json.dumps(record("ios","d"*64))); assert subprocess.run(base).returncode!=0
