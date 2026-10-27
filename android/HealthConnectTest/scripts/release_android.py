"""Publish a built Android APK and update manifest from the canonical monorepo."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--metadata', type=Path, required=True)
parser.add_argument('--notes', type=Path, required=True)
args = parser.parse_args()
repo = 'Uriel1010/health-dashboard'
metadata = json.loads(args.metadata.read_text())
assert metadata['applicationId'] == 'com.uriel.healthconnecttest'
element = next(item for item in metadata['elements'] if item['outputFile'] == args.apk.name)
code, version = element['versionCode'], element['versionName']
assert isinstance(code, int) and code > 1
assert all(part.isdigit() for part in version.split('.')) and len(version.split('.')) == 3
assert not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no']).strip(), 'Commit the source before publishing'
tag = 'android-v' + version
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
hash_value = hashlib.file_digest(args.apk.open('rb'), 'sha256').hexdigest()
manifest = dict(versionCode=code, versionName=version, minSdk=28, sha256=hash_value,
    apkUrl=f'https://github.com/{repo}/releases/download/{tag}/{args.apk.name}')
with tempfile.TemporaryDirectory(prefix='health-android-release-') as folder:
    folder = Path(folder)
    (folder / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (folder / 'SHA256SUMS').write_text(f'{hash_value}  {args.apk.name}\n')
    subprocess.run(['gh', 'release', 'create', tag, str(args.apk.resolve()), str(folder / 'update.json'),
        str(folder / 'SHA256SUMS'), '--repo', repo, '--target', head, '--title', f'Android {version}',
        '--prerelease', '--notes-file', str(args.notes.resolve())], check=True)
