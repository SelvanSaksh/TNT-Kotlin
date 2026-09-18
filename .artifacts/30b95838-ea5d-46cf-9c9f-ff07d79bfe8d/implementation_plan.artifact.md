# Play Store Update: Version Bump and AAB Generation

This plan covers updating the application version, building the Android App Bundle (AAB), and delivering it to the user's desktop for deployment.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///D:/schoofi/TNT-Kotlin/composeApp/build.gradle.kts)
- Increment `versionCode` from `3` to `4`.
- Update `versionName` from `"1.0.2"` to `"1.0.3"`.

## Verification Plan

### Automated Steps
1. **Gradle Build:** Execute `:composeApp:bundleRelease` to generate the AAB file.
2. **File Validation:** Verify the existence of the AAB file at `composeApp/build/outputs/bundle/release/composeApp-release.aab`.
3. **Delivery:** Copy the AAB file to `C:\Users\mutha_rx3lv8e\Desktop`.

### Manual Verification
- User to verify the AAB file is present on their desktop and ready for Play Store upload.
