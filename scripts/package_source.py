#!/usr/bin/env python3
"""Create the reviewed source archive without Git history, local keys or builds."""
from pathlib import Path
import hashlib, subprocess, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
EXCLUDED={'.git','.gradle','.local-build','build','builds','__pycache__','.venv','.wallpaper-backups','.DS_Store'}
subprocess.run([sys.executable,str(ROOT/'scripts/check_public_release.py')],check=True)
output=ROOT.parent/'luma-tv-public-source.zip'
with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in sorted(ROOT.rglob('*')):
        rel=p.relative_to(ROOT)
        if p.is_file() and not any(part in EXCLUDED for part in rel.parts):
            if p.is_symlink():raise SystemExit('Symlinks are not publishable')
            z.write(p,'luma-tv/'+rel.as_posix())
print(output.name,hashlib.sha256(output.read_bytes()).hexdigest())
