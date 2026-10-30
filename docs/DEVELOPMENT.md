# Development

## Toolchain

- JDK 17 (CI); JDK 21 also works locally.
- Android SDK platform 35 and Build Tools 35.0.0.
- Gradle 8.11.1 wrapper; its distribution SHA-256 is pinned.
- Android Gradle Plugin 8.7.3.
- Minimum supported Android version: API 26.

The project has no third-party runtime dependencies. Test dependencies are JUnit, JSON-java and Robolectric.

## Checks

```sh
python3 scripts/check_localization.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Focused examples:

```sh
./gradlew :app:testDebugUnitTest --tests '*OAuthClientTest*' --tests '*AuthSessionTest*'
./gradlew :app:testDebugUnitTest --tests '*PendingLoginTest*'
./gradlew :app:testDebugUnitTest --tests '*WidgetRenderTest*' --tests '*AppDialogsTest*'
```

Test coverage includes device authorization with a fake transport, encrypted storage, cancellation, account-switch races, token rotation, reset idempotency, configurable background scheduling, and date formats. Tests do not contact a real account or apply real resets.

Robolectric native-graphics checks run on API 26 and 35. The widget grid covers 1×1–6×6, transition sizes, both themes and enlarged fonts. It checks clipping and excessive vertical gaps. Native framework rendering is useful but does not reproduce every launcher-specific behavior.

Reports and generated renders:

- `app/build/reports/tests/testDebugUnitTest/`
- `app/build/reports/lint-results-debug.html`
- `app/build/widget-previews/`
- `app/build/widget-grid/`

## Preview assets

To regenerate launcher preview images from Android layouts using sample data:

```sh
./gradlew :app:testDebugUnitTest --tests '*WidgetRenderTest.exportLauncherPreviews' -PupdateWidgetPreviews=true
```

This explicit command updates `app/src/main/res/drawable-nodpi/preview_*.png`, their dark-theme counterparts, and the Russian `drawable-ru[-night]-nodpi` variants. Normal tests do not modify source resources.

To refresh the documentation images:

```sh
./gradlew :app:exportDocumentationImages
```

Do not replace demonstration images with raw screenshots containing account details or authorization codes.

## Repository layout

`app/src/main/` contains production code and Android resources; `app/src/test/` contains local tests. `docs/` describes architecture and maintenance; `release-notes/` contains user-facing release notes. Build outputs, signing material, local research archives and original reference screenshots are ignored.

Use `./gradlew clean` to remove generated Gradle build outputs. Keep the wrapper JAR and packaged preview images: they are required project assets, not temporary files.
