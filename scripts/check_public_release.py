#!/usr/bin/env python3
"""Fail closed on unreviewed assets, private configuration and device identifiers."""
from pathlib import Path
import argparse, hashlib, io, json, re, sys, zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
IGNORE={'.git','.gradle','.local-build','build','builds','__pycache__','.venv','.wallpaper-backups','.DS_Store'}
ASSETS=ROOT/'app/src/main/assets'
catalog=json.loads((ASSETS/'wallpapers/catalog.json').read_text())
allowed_images={}
for item in catalog:
    for name,hash_key in [('file','sha256'),('thumb','thumb_sha256')]:
        allowed_images['app/src/main/assets/wallpapers/'+item[name]]=item[hash_key]
media_manifest=ROOT/'public-media.json'
if media_manifest.exists():allowed_images.update(json.loads(media_manifest.read_text()))
rules={
    'private network address':rb'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b',
    'local home directory':rb'/(?:Users|home)/[^/\s]+',
    'personal photo filename':rb'IMG_\d{4}',
    'excluded companion':('baby'+'view|Luma '+'Cam').encode(),
    'credential URL':rb'(?:https?|rtsp)://[^/\s:@]+:[^/\s@]+@',
    'private key':rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
}
errors=[]
def check(name,data,image_path=None):
    for label,pattern in rules.items():
        if re.search(pattern,data):errors.append(f'{name}: {label}')
    if image_path:
        expected=allowed_images.get(image_path)
        approved=expected and hashlib.sha256(data).hexdigest()==expected
        # aapt2 may losslessly re-encode drawable PNGs in Gradle builds.
        if not approved and name.startswith('APK/res/') and expected:
            original=ROOT/image_path
            if original.is_file() and hashlib.sha256(original.read_bytes()).hexdigest()==expected:
                with Image.open(original) as a, Image.open(io.BytesIO(data)) as b:
                    approved=a.size==b.size and a.convert('RGBA').tobytes()==b.convert('RGBA').tobytes()
        if not approved:errors.append(f'{name}: unreviewed image')
        try:
            im=Image.open(io.BytesIO(data))
            if im.getexif():errors.append(f'{name}: EXIF metadata present')
            if im.info.get('xmp') or im.info.get('XML:com.adobe.xmp'):errors.append(f'{name}: XMP metadata present')
        except Exception:errors.append(f'{name}: invalid image')
def source_files():
    for p in sorted(ROOT.rglob('*')):
        rel=p.relative_to(ROOT)
        if p.is_file() and not any(part in IGNORE for part in rel.parts):yield p,rel.as_posix()
for p,name in source_files():
    if p.is_symlink():errors.append(f'{name}: symlink not allowed');continue
    if p.suffix.lower() in {'.keystore','.jks','.pem','.key','.apk','.aab','.heic','.mp4','.mov'} or p.name.startswith('.env'):
        errors.append(f'{name}: private or build file')
    if name.startswith('app/src/main/assets/wallpapers/') and p.suffix.lower() in {'.jpg','.jpeg','.png','.webp'} and name not in allowed_images:
        errors.append(f'{name}: non-public wallpaper')
    if p.name in {'weather-location.json','tv6-profile.json','local.properties'}:errors.append(f'{name}: local configuration')
    data=p.read_bytes()
    check(name,data,name if p.suffix.lower() in {'.jpg','.jpeg','.png','.webp'} else None)
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
if re.search(r'android.permission.(?:CAMERA|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)',manifest):errors.append('manifest: private-device permission')
gradle=(ROOT/'app/build.gradle').read_text()
if "applicationId 'io.github.op7418.lumatv.publicui'" not in gradle:errors.append('public package identity changed')
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apk',type=Path);args=parser.parse_args()
if args.apk:
    with zipfile.ZipFile(args.apk) as apk:
        names=apk.namelist()
        expected_assets={str(p.relative_to(ASSETS)).replace('\\','/') for p in ASSETS.rglob('*') if p.is_file()}
        actual_assets={name[len('assets/'):] for name in names if name.startswith('assets/') and not name.endswith('/')}
        if actual_assets!=expected_assets:errors.append('APK: assets do not match reviewed source')
        for name in names:
            if name.endswith('/'):continue
            data=apk.read(name)
            image_path='app/src/main/'+name if name.startswith(('assets/','res/')) and name.lower().endswith(('.jpg','.jpeg','.png','.webp')) else None
            if image_path and image_path not in allowed_images:
                image_path=re.sub(r'/drawable(?:-[^/]+)?/', '/drawable/',image_path)
            check('APK/'+name,data,image_path)
            if name.startswith('assets/') and hashlib.sha256(data).digest()!=hashlib.sha256((ASSETS/name[7:]).read_bytes()).digest():
                errors.append(f'APK/{name}: asset content differs')
if errors:
    print('\n'.join(errors));sys.exit(1)
print(f'PASS: {sum(1 for _ in source_files())} source files, {len(allowed_images)} reviewed images'+(' and APK contents' if args.apk else ''))
