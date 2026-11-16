# Chess External — Android overlay build

Android chess trainer overlay based on [OpenCheps](https://github.com/Sethdsai/opencheps) (MIT).

This repository builds OpenCheps from pinned upstream commit `b1eadc58d8dcf96f84976ccf49ac6d3c1be2ccc0`, patches both overlay windows with Android `FLAG_SECURE`, substitutes official ARM64 / ARMv7 **Stockfish 19** binaries, and produces debug APK artifacts via GitHub Actions.

## Download APK

Open **Actions** → **Build Chess External APK** → latest successful run → **Artifacts** → `Chess-External-arm64-debug`. Download the ZIP and extract the APK. Android 15 arm64 devices should use arm64. No Termux required.

## Important limitations

`FLAG_SECURE` is not a guarantee that the overlay will be simply invisible while screen recording. Some Android devices black out the secure region or the entire screen; this must be tested before relying on it. A full-screen secure surface may also interfere with the app's own MediaProjection capture and classification. Root does not automatically resolve this.

Original OpenCheps uses visual recognition that may misclassify pieces, and move legality can be limited. This build doesn't claim perfect board recognition. The earlier browser plugin's random-quality slider and promotion chooser are not yet ported.

Engine hints in live games against human opponents may violate service fair-play rules. Use for offline study, bot play, and permitted analysis.

## Licenses

OpenCheps source: MIT, see `LICENSE-UPSTREAM`.
Official Stockfish: GPL-3.0, https://github.com/official-stockfish/Stockfish ; downloaded binary unmodified from the official GitHub release with SHA-256 validation.
