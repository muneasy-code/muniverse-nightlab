# MUNIVERSE · NightLab 🌌

Native Android astrophotography alpha built for Samsung Galaxy S23.

## v0.1 — experimental hardware test
- S23 Camera2 capability report and camera preview
- Manual ISO / exposure on devices which expose MANUAL_SENSOR
- JPG + true RAW_SENSOR DNG where supported; fallback to JPEG-only when stream combination fails
- Offline sky mission planner and pointing compass
- Locally processed JPG editing without generative AI

**Status:** unverified Android alpha. Samsung Expert RAW multi-minute stacking is proprietary and NOT implemented here. A Camera2 DNG is not automatically the same as a Samsung Expert RAW DNG. Test on the S23 before relying on captures.

## Install
Open **Actions → Build MUNIVERSE test APK**, wait for the run, download artifact `MUNIVERSE-debug-APK`, extract and install `app-debug.apk` on the S23. Android may prompt to allow installation from that source.

No login, analytics, camera image upload or network permission. Use a safe phone mount; never place the phone in vehicle traffic.

## Next
Validate the Camera2 capability report and first JPEG/DNG on the actual Galaxy S23. Then add automated capture series, stacking and full real-DNG development.
