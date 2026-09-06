#!/usr/bin/env python3
"""Summarize packaged artifact sizes as a GitHub annotation (compact, review-friendly)."""
import glob
import io
import os
import sys
import zipfile

def mb(value):
    return f"{value / 1048576:.1f} MB"

def category(name):
    if name.startswith("lib/"):
        parts = name.split("/")
        return "lib/" + (parts[1] if len(parts) > 2 else "?")
    if name.endswith(".dex"):
        return "dex"
    if name.startswith("res/") or name == "resources.arsc":
        return "resources"
    if name.startswith("assets/"):
        return "assets"
    if name.startswith("META-INF/"):
        return "META-INF"
    return "other"

def collect(path):
    """Return [(name, compressed_size)] for an APK, or the base module of an AAB."""
    with zipfile.ZipFile(path) as archive:
        if "base.zip" in archive.namelist():
            with zipfile.ZipFile(io.BytesIO(archive.read("base.zip"))) as base:
                return [(info.filename, info.compress_size) for info in base.infolist()]
        return [(info.filename, info.compress_size) for info in archive.infolist()]

def summarize(path):
    # Single line on purpose: raw newlines break workflow command syntax in annotations.
    entries = collect(path)
    cats = {}
    for name, size in entries:
        cat = category(name)
        cats[cat] = cats.get(cat, 0) + size
    ordered = sorted(cats.items(), key=lambda kv: -kv[1])
    biggest = "; ".join(f"{n} {mb(s)}" for n, s in sorted(entries, key=lambda p: -p[1])[:6])
    return (f"{os.path.relpath(path, 'app/build/outputs')}: on disk {mb(os.path.getsize(path))}, "
            f"payload {mb(sum(s for _, s in entries))}, " + " · ".join(f"{name} {mb(size)}" for name, size in ordered)
            + " || largest: " + biggest)

def main():
    paths = sys.argv[1:] or sorted(
        glob.glob("app/build/outputs/apk/*/*.apk") + glob.glob("app/build/outputs/bundle/release/*.aab"))
    notes = []
    for path in paths:
        try:
            notes.append(summarize(path))
        except Exception as error:  # reporting must never fail the build
            notes.append(f"{path}: size reporting failed ({error})")
    if notes:
        print("::notice title=APK sizes::" + "%0A".join(notes))

if __name__ == "__main__":
    main()
