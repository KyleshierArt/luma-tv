#!/usr/bin/env python3
"""Build with a local SDK. Does not connect to or install on devices."""
from pathlib import Path
import argparse, os, shutil, subprocess, sys, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
PACKAGE='io.github.op7418.lumatv.publicui'
NAMESPACE='io.github.op7418.guizangtv'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sdk',default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
parser.add_argument('--debug-sign',action='store_true',help='Sign with a separate local preview certificate')
args=parser.parse_args()
if not args.sdk:parser.error('Set ANDROID_HOME or pass --sdk')
sdk=Path(args.sdk).resolve()
java=Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or '').resolve().parents[1]
tools=next((sdk/'build-tools').glob('*/aapt2')).parent
android=next((sdk/'platforms').glob('android-34*/android.jar'))
main=ROOT/'app/src/main'
env=dict(os.environ,JAVA_HOME=str(java),PATH=str(java/'bin')+os.pathsep+os.environ['PATH'])
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,env=env)
run(sys.executable,ROOT/'scripts/generate_translations.py')
run(sys.executable,ROOT/'scripts/check_public_release.py')
with tempfile.TemporaryDirectory(prefix='luma-public-build-') as tmp:
    work=Path(tmp)
    for name in ['generated','classes','dex']:(work/name).mkdir()
    ns='{http://schemas.android.com/apk/res/android}'
    ET.register_namespace('android',ns[1:-1])
    tree=ET.parse(main/'AndroidManifest.xml');tree.getroot().set('package',NAMESPACE)
    for component in tree.find('application'):
        name=component.get(ns+'name','')
        if name.startswith('.'):component.set(ns+'name',NAMESPACE+name)
    tree.write(work/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    run(tools/'aapt2','compile','--no-crunch','--dir',main/'res','-o',work/'resources.zip')
    run(tools/'aapt2','link','-o',work/'resources.apk','-I',android,'--manifest',work/'AndroidManifest.xml',
        '--java',work/'generated','--custom-package',NAMESPACE,'--rename-manifest-package',PACKAGE,
        '--min-sdk-version','26','--target-sdk-version','34','--version-code','1','--version-name','0.6.0-public',
        '-A',main/'assets',work/'resources.zip')
    sources=sorted((main/'java').rglob('*.java'))+sorted((work/'generated').rglob('*.java'))
    run(java/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',
        str(tools/'core-lambda-stubs.jar')+os.pathsep+str(android),'-d',work/'classes',*sources)
    run(java/'bin/jar','cf',work/'classes.jar','-C',work/'classes','.')
    run(tools/'d8','--release','--min-api','26','--lib',android,'--output',work/'dex',work/'classes.jar')
    shutil.copy2(work/'resources.apk',work/'unsigned.apk')
    with zipfile.ZipFile(work/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted((work/'dex').glob('*.dex')):apk.write(dex,dex.name)
    run(tools/'zipalign','-f','4',work/'unsigned.apk',work/'aligned.apk')
    output=ROOT/'builds'/('luma-tv-public-preview.apk' if args.debug_sign else 'luma-tv-public-unsigned.apk')
    output.parent.mkdir(exist_ok=True)
    if args.debug_sign:
        key=ROOT/'.local-build/public-preview.keystore';key.parent.mkdir(exist_ok=True)
        if not key.exists():
            run(java/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
                '-alias','public-preview','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Luma TV Public Preview')
        run(tools/'apksigner','sign','--ks',key,'--ks-key-alias','public-preview','--ks-pass','pass:android',
            '--key-pass','pass:android','--v4-signing-enabled','false','--out',output,work/'aligned.apk')
        run(tools/'apksigner','verify','--verbose',output)
    else:shutil.copy2(work/'aligned.apk',output)
    run(sys.executable,ROOT/'scripts/check_public_release.py','--apk',output)
    print('Built:',output)
