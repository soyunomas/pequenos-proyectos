# Flux Files v1.0.0 — source provenance

Flux Files is a rebranded build of Material Files, pinned to upstream commit `fc1250038496ebf4d4c139f62d16f0071f2c995a` (Material Files 1.7.4), with only these build-time changes:

- Android application ID: `me.zhanghai.android.files` → `dev.local.fluxfiles`
- App display name: `Material Files` → `Flux Files`
- Version name: `1.7.4` → `1.7.4-flux.1`
- NONFREE/Firebase/Crashlytics/Google Services blocks removed for this FOSS build.
- dav4jvm pinned to `02fe1a95e6b86e323bec3784d7d2fe2d4081dde6` and compiled locally with Kotlin module name `build` to preserve the ABI expected by Material Files.

The exact transformation and publication recipe are in `.github/workflows/build-flux-files-apk.yml`.

Upstream source: https://github.com/zhanghai/MaterialFiles

Material Files is licensed under GNU GPL v3. The upstream copyright and license notices remain applicable. This derivative must be distributed under the same GPLv3 terms.
