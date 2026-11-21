#!/usr/bin/env bash
set -euo pipefail
abi="$1"
case "$abi" in x86_64|arm64-v8a) ;; *) exit 2 ;; esac
script_dir="$(cd "$(dirname "$0")" && pwd)"
source_commit='4cf121e515d8d065b57cd555a0b231ea27ff0e5d'
patch_sha='9d3ff4ac5d6ae1c6e74b0f5dead516d9e32d540b6ed7f5d8cb63fd2b76bc8af0'
patch="$script_dir/0011-opengl-uniform-block-order.patch"
echo "$patch_sha  $patch" | sha256sum --check
work="$RUNNER_TEMP/maplibre-native"
git clone --depth 1 --branch android-v13.7.1 --no-checkout https://github.com/maplibre/maplibre-native.git "$work"
git -C "$work" checkout --detach "$source_commit"
test "$(git -C "$work" rev-parse HEAD)" = "$source_commit"
git -C "$work" submodule update --init --recursive --depth 1
git -C "$work" apply --check "$patch"
git -C "$work" apply "$patch"
git -C "$work" diff --check
# Build the reviewed source fix. Never byte-patch a compiled native library.
cd "$work/platform/android"
./gradlew :MapLibreAndroid:assembleOpenglRelease -Pmaplibre.abis="$abi" --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g --stacktrace
export QG_MAPLIBRE_ABI="$abi" QG_MAPLIBRE_SOURCE="$source_commit" QG_MAPLIBRE_PATCH="$patch_sha"
python3 - <<'PY'
import hashlib,json,os,pathlib,shutil,subprocess,tempfile,zipfile
root=pathlib.Path(os.environ['RUNNER_TEMP'])
archives=list((root/'maplibre-native/platform/android/MapLibreAndroid/build/outputs/aar').glob('*.aar'))
assert len(archives)==1,archives
output=root/'maplibre-compat-output'
output.mkdir(exist_ok=True)
artifact=output/'maplibre-opengl-13.7.1-uniform4625.aar'
shutil.copyfile(archives[0],artifact)
abi=os.environ['QG_MAPLIBRE_ABI']
with zipfile.ZipFile(artifact) as package, tempfile.TemporaryDirectory() as temp:
    libraries=[n for n in package.namelist() if n.startswith('jni/') and n.endswith('.so')]
    assert libraries and all(n.split('/')[1]==abi for n in libraries),libraries
    readelf=pathlib.Path(os.environ['ANDROID_HOME'])/'ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    for index,name in enumerate(libraries):
        native=pathlib.Path(temp)/f'{index}.so'
        native.write_bytes(package.read(name))
        report=subprocess.check_output([str(readelf),'-lW',str(native)],text=True)
        (output/f'elf-{index}.txt').write_text(report)
        loads=[line.split() for line in report.splitlines() if line.strip().startswith('LOAD ')]
        assert loads and all(int(line[-1],16)>=16384 for line in loads),report
digest=hashlib.file_digest(artifact.open('rb'),'sha256').hexdigest()
(output/(artifact.name+'.sha256')).write_text(digest+'  '+artifact.name+'\n')
(output/'provenance.json').write_text(json.dumps({
    'upstream':'https://github.com/maplibre/maplibre-native',
    'version':'13.7.1','source_commit':os.environ['QG_MAPLIBRE_SOURCE'],
    'patch_source':'https://github.com/maplibre/maplibre-native-ffi/commit/fd5c26f94314ce077caf6ddc895e7142f3afe38e',
    'upstream_review':'https://github.com/maplibre/maplibre-native/pull/4625',
    'patch_sha256':os.environ['QG_MAPLIBRE_PATCH'],'abi':abi,
    'builder_commit':os.environ.get('GITHUB_SHA'),'aar_sha256':digest,
    'aar_bytes':artifact.stat().st_size,'ndk':'28.2.13676358',
    'installed_rider_qa':False,'physical_device_qa':False,
    'purpose':'Source-built dependency candidate; must be integrated and pixel-tested before acceptance'
},indent=2)+'\n')
print(json.dumps({'abi':abi,'aar_bytes':artifact.stat().st_size,'sha256':digest}))
PY
