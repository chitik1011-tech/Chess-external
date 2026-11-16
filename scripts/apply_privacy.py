#!/usr/bin/env python3
"""Apply Android FLAG_SECURE to both OpenCheps overlay windows, failing closed."""
from pathlib import Path
import sys

BASE = Path(__file__).resolve().parent.parent
PROJECT = Path(sys.argv[1]).resolve() if len(sys.argv) >= 2 else BASE
TARGETS = (
    'app/src/main/java/com/opencheps/overlay/OverlayService.java',
    'app/src/main/java/com/opencheps/overlay/FloatingPanel.java',
)
NEEDLE = 'WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,'
REPLACEMENT = ('WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS\n'
               '                        | WindowManager.LayoutParams.FLAG_SECURE,')

def patch_one(p: Path) -> str:
    if not p.is_file():
        raise FileNotFoundError(f'Upstream source file not found: {p}')
    content = p.read_text(encoding='utf-8')
    if 'WindowManager.LayoutParams.FLAG_SECURE' in content:
        if REPLACEMENT not in content:
            raise ValueError(f'{p}: unexpected existing FLAG_SECURE use')
        return 'already secure'
    if content.count(NEEDLE) != 1:
        raise ValueError(f'{p}: expected exactly one layout flag anchor, found {content.count(NEEDLE)}')
    p.write_text(content.replace(NEEDLE, REPLACEMENT, 1), encoding='utf-8')
    return 'patched'

def main():
    for rel in TARGETS:
        print(f'{rel}: {patch_one(PROJECT / rel)}')

if __name__ == '__main__':
    main()
