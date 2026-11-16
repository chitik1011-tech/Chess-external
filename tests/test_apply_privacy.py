import tempfile
import unittest
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = 'app/src/main/java/com/opencheps/overlay'
TEXT = '''package com.opencheps.overlay;
import android.view.WindowManager;
class Test {
 int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
}
'''

class PatchTests(unittest.TestCase):
    def test_secure_flags_on_both_windows_and_idempotence(self):
        with tempfile.TemporaryDirectory() as d:
            for n in ('OverlayService.java', 'FloatingPanel.java'):
                p = Path(d, BASE, n)
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(TEXT)
            for _ in range(2):
                subprocess.run([sys.executable, str(ROOT/'scripts/apply_privacy.py'), d], check=True)
            for n in ('OverlayService.java','FloatingPanel.java'):
                content = Path(d, BASE, n).read_text()
                self.assertEqual(content.count('WindowManager.LayoutParams.FLAG_SECURE'), 1)

    def test_fails_closed_on_upstream_change(self):
        with tempfile.TemporaryDirectory() as d:
            for n in ('OverlayService.java', 'FloatingPanel.java'):
                p = Path(d, BASE, n)
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text('class Test {}')
            r = subprocess.run([sys.executable, str(ROOT/'scripts/apply_privacy.py'), d], capture_output=True, text=True)
            self.assertNotEqual(r.returncode, 0)
            self.assertIn('expected exactly one', r.stderr)

if __name__ == '__main__':
    unittest.main()
