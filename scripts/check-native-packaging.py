#!/usr/bin/env python3
"""Check that executable native payloads exist, and all shipped 64-bit ELF LOAD segments support 16 KB pages."""
import glob
import io
import struct
import sys
import zipfile

failures = []
checked = 0

def inspect_elf(name, payload):
    global checked
    if not payload.startswith(b'\x7fELF') or payload[4] != 2:
        return
    endian = '<' if payload[5] == 1 else '>'
    offset = struct.unpack_from(endian + 'Q', payload, 32)[0]
    entry_size, count = struct.unpack_from(endian + 'HH', payload, 54)
    checked += 1
    for index in range(count):
        pos = offset + index * entry_size
        kind = struct.unpack_from(endian + 'I', payload, pos)[0]
        if kind == 1:
            file_offset, address = struct.unpack_from(endian + 'QQ', payload, pos + 8)
            alignment = struct.unpack_from(endian + 'Q', payload, pos + 48)[0]
            if alignment < 16384 or (address - file_offset) % 16384:
                failures.append(f'{name}: LOAD alignment {alignment} is not 16 KB compatible')
                break

apks = sys.argv[1:] or glob.glob('app/build/outputs/apk/debug/*.apk')
if not apks:
    raise SystemExit('No APK to inspect')
for apk_path in apks:
    with zipfile.ZipFile(apk_path) as apk:
        names = set(apk.namelist())
        for abi in ('arm64-v8a', 'x86_64'):
            for library in ('libpython.so', 'libpython.zip.so', 'libffmpeg.so', 'libffmpeg.zip.so', 'libqjs.so'):
                path = f'lib/{abi}/{library}'
                if path not in names:
                    failures.append(f'{apk_path}: missing {path}')
        for entry in apk.infolist():
            if entry.filename.startswith('lib/') and entry.filename.endswith('.so'):
                payload = apk.read(entry)
                if payload.startswith(b'PK'):
                    with zipfile.ZipFile(io.BytesIO(payload)) as nested:
                        for nested_entry in nested.infolist():
                            if not nested_entry.is_dir():
                                inspect_elf(entry.filename + '!' + nested_entry.filename, nested.read(nested_entry))
                else:
                    inspect_elf(entry.filename, payload)
if failures:
    for message in failures:
        print('::error title=Native packaging::' + message)
    raise SystemExit(1)
print(f'::notice title=Native packaging::Verified packaged executables and {checked} 64-bit ELF payloads for 16 KB LOAD alignment.')
