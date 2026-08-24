#!/usr/bin/env python3
"""Extract the ten Clementime frame PNGs from the bundled APK."""
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "Clementime_by_Clemente_v1.3.apk"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)

with ZipFile(APK) as zf:
    for name in zf.namelist():
        if name.startswith("res/drawable/theme_") and name.endswith(".png"):
            (OUT / Path(name).name).write_bytes(zf.read(name))
            print(Path(name).name)
