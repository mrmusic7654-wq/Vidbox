"""Expose useful build diagnostics through the Checks API, in addition to the normal CI artifacts."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

status = int(sys.argv[1])
if os.environ.get('GITHUB_ACTIONS'):
    if status:
        with open(sys.argv[2], errors='replace') as f:
            tail = ''.join(f.readlines()[-200:])
        for offset in range(0, min(len(tail), 48000), 6000):
            message = tail[offset:offset + 6000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f'::error title=Gradle diagnostic::{message}')
    else:
        tests = failures = errors = skipped = 0
        for path in glob.glob('**/build/test-results/**/TEST-*.xml', recursive=True):
            root = ET.parse(path).getroot()
            tests += int(root.get('tests', 0))
            failures += int(root.get('failures', 0))
            errors += int(root.get('errors', 0))
            skipped += int(root.get('skipped', 0))
        print(f'::notice title=Verification::Gradle succeeded. JUnit cases={tests}, failures={failures}, errors={errors}, skipped={skipped}.')
