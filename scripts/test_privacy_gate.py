"""Exercise publication boundaries using synthetic data in temporary copies."""
from pathlib import Path
import shutil, subprocess, sys, tempfile, unittest, zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]

class PublicationBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='luma-boundary-test-')
        self.addCleanup(self.tmp.cleanup)
        self.repo=Path(self.tmp.name)/'source'
        shutil.copytree(ROOT,self.repo,ignore=shutil.ignore_patterns(
            '.git','.gradle','.local-build','build','builds','__pycache__','.venv','.wallpaper-backups'))

    def check_gate(self,*args):
        return subprocess.run([sys.executable,str(self.repo/'scripts/check_public_release.py'),*map(str,args)],capture_output=True,text=True)

    def test_reviewed_source_passes(self):
        result=self.check_gate()
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_local_location_configuration_is_rejected(self):
        (self.repo/'app/src/main/assets/weather-location.json').write_text('{"latitude":0,"longitude":0}')
        self.assertNotEqual(0,self.check_gate().returncode)

    def test_new_unreviewed_image_is_rejected(self):
        Image.new('RGB',(10,10),'black').save(self.repo/'app/src/main/assets/wallpapers/unreviewed.png')
        self.assertNotEqual(0,self.check_gate().returncode)

    def test_embedded_network_address_is_rejected(self):
        address='192.'+'168.'+'4.'+'9'
        (self.repo/'connection-example.txt').write_text(address)
        self.assertNotEqual(0,self.check_gate().returncode)

    def test_unexpected_apk_asset_is_rejected(self):
        assets=self.repo/'app/src/main/assets'
        archive=Path(self.tmp.name)/'synthetic.apk'
        with zipfile.ZipFile(archive,'w') as z:
            for p in assets.rglob('*'):
                if p.is_file():z.write(p,'assets/'+str(p.relative_to(assets)))
            z.writestr('assets/unreviewed-config.json','{}')
        self.assertNotEqual(0,self.check_gate('--apk',archive).returncode)

if __name__=='__main__':unittest.main()
