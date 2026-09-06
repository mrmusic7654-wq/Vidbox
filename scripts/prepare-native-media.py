#!/usr/bin/env python3
"""Rebuild WebP from pinned source and prepare the real Android FFmpeg payload, without editing ELF headers."""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

# Must stay aligned with the ABIs the app actually packages (see app ndk.abiFilters).
ABIS = ('arm64-v8a', 'x86_64')
LIBRARIES = ('libwebp', 'libwebpdecoder', 'libwebpdemux', 'libwebpmux', 'libsharpyuv')
TARGETS = ('webp', 'webpdecoder', 'webpdemux', 'libwebpmux', 'sharpyuv')

def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        while block := source.read(65536):
            digest.update(block)
    return digest.hexdigest()

def properties(path):
    return dict(line.strip().split('=', 1) for line in path.read_text().splitlines()
                if line.strip() and not line.lstrip().startswith('#'))

def main():
    parser = argparse.ArgumentParser()
    for name in ('aar', 'work', 'output', 'sdk', 'root'):
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    lock = properties(args.root / 'native-deps.lock')
    args.work.mkdir(parents=True, exist_ok=True)
    archive = args.work / ('libwebp-' + lock['webp.version'] + '.tar.gz')
    if not archive.is_file() or sha256(archive) != lock['webp.sha256']:
        partial = archive.with_suffix('.download')
        try:
            request = urllib.request.Request(lock['webp.url'], headers={'User-Agent': 'Vidbox-build/1.0'})
            with urllib.request.urlopen(request, timeout=60) as response, partial.open('wb') as target:
                shutil.copyfileobj(response, target, 65536)
            if sha256(partial) != lock['webp.sha256']:
                raise RuntimeError('libwebp source checksum mismatch')
            partial.replace(archive)
        finally:
            partial.unlink(missing_ok=True)
    sources = args.work / 'sources'
    expected_source = sources / ('libwebp-' + lock['webp.version'])
    if not expected_source.is_dir():
        sources.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as package:
            # No absolute paths, traversal, links, or device nodes are accepted from the source archive.
            for entry in package.getmembers():
                destination = (sources / entry.name).resolve()
                if not destination.is_relative_to(sources.resolve()) or entry.issym() or entry.islnk() or not (entry.isfile() or entry.isdir()):
                    raise RuntimeError('Unsafe native source archive entry')
            package.extractall(sources, filter='data')
    ndk = args.sdk / 'ndk' / lock['ndk.version']
    suffix = '.exe' if os.name == 'nt' else ''
    cmake = args.sdk / 'cmake' / lock['cmake.version'] / 'bin' / ('cmake' + suffix)
    ninja = cmake.with_name('ninja' + suffix)
    if not cmake.is_file() or not (ndk / 'build/cmake/android.toolchain.cmake').is_file():
        raise RuntimeError('Install the exact NDK and CMake versions listed in native-deps.lock using sdkmanager')
    with zipfile.ZipFile(args.aar) as upstream:
        for abi in ABIS:
            build = args.work / abi
            command = [str(cmake), '-S', str(args.root / 'data/src/main/cpp'), '-B', str(build), '-G', 'Ninja',
                       '-DCMAKE_MAKE_PROGRAM=' + str(ninja), '-DCMAKE_BUILD_TYPE=Release',
                       '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
                       '-DANDROID_ABI=' + abi, '-DANDROID_PLATFORM=android-29',
                       '-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON', '-DWEBP_SOURCE_DIR=' + str(expected_source)]
            subprocess.run(command, check=True)
            subprocess.run([str(cmake), '--build', str(build), '--target', *TARGETS, '--parallel', '2'], check=True)
            replacements = {name: build / 'lib' / (name + '.so') for name in LIBRARIES}
            if not all(path.is_file() for path in replacements.values()):
                raise RuntimeError('The Android codec build did not produce all required shared libraries')
            destination = args.output / abi
            destination.mkdir(parents=True, exist_ok=True)
            # libffprobe.so is deliberately not copied: Vidbox only ever execs libffmpeg.so as the
            # CLI, and shipping an unused second static-linked binary would double the ffmpeg payload.
            for tool in ('libffmpeg.so',):
                (destination / tool).write_bytes(upstream.read('jni/' + abi + '/' + tool))
            original = upstream.read('jni/' + abi + '/libffmpeg.zip.so')
            seen = set()
            with zipfile.ZipFile(io.BytesIO(original)) as runtime, zipfile.ZipFile(destination / 'libffmpeg.zip.so', 'w', zipfile.ZIP_DEFLATED) as patched:
                for entry in runtime.infolist():
                    name = Path(entry.filename).name.split('.so')[0]
                    if name in replacements and '.so' in entry.filename:
                        payload = replacements[name].read_bytes()
                        seen.add(name)
                    else:
                        payload = runtime.read(entry)
                    patched.writestr(entry, payload)
                for name in ('COPYING', 'PATENTS'):
                    info = zipfile.ZipInfo('usr/share/licenses/vidbox-libwebp/' + name, date_time=(2026, 1, 1, 0, 0, 0))
                    patched.writestr(info, (expected_source / name).read_bytes())
            if seen != set(LIBRARIES):
                raise RuntimeError('Upstream FFmpeg archive layout changed; review the codec replacement mapping')
            print('Prepared', abi, 'FFmpeg with source-built 16 KB WebP libraries')

if __name__ == '__main__':
    main()
