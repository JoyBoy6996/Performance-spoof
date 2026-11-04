# ABenchMark – source reconstruction

This repository contains a source-level Java reconstruction of the uploaded
APK's app-owned classes, plus decoded Android resources and GitHub Actions.

- Package: `com.antutu.ABenchMark`
- Activity: `com.antutu.ABenchMark.MainActivity`
- Service: `com.antutu.ABenchMark.PerformanceService`
- Nested service classes retain their original binary names.
- Activity background delay: original 500 ms.
- Resume background delay: original 200 ms.
- Service heartbeat: original 3000 ms.
- No Activity buttons or replacement UI have been added.
- No original APK or `original_payload.zip` is included.

## Build

1. Extract this ZIP into the root of a GitHub repository.
2. Push to `main` or `master`.
3. Open **Actions → Build ABenchMark source reconstruction**.
4. Download the `ABenchMark-source-build` artifact.

See [RECONSTRUCTION.md](RECONSTRUCTION.md) for the recovered behavior and
known limits of decompiling compiled DEX back into source.
