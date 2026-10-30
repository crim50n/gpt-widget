# Contributing

## Development

Use JDK 17, Android SDK platform 35 and Build Tools 35.0.0. The checked-in Gradle wrapper downloads and verifies Gradle 8.11.1.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug builds use `dev.local.usagewidget.debug` and can coexist with the signed release.

## Changes

- Keep application code native Java/Android SDK unless a dependency is justified.
- Keep account data, access tokens, refresh tokens and device codes out of logs, fixtures, screenshots and issues.
- Test changes to authentication, account switching and reset idempotency with fake transports; tests must not consume a real reset or authorize a real account.
- Widget changes should be checked in both themes, compact and stretched layouts, and enlarged font sizes. See [development](docs/DEVELOPMENT.md).
- Keep the English and Russian README aligned with actual behavior.
- Put English UI text in `res/values/strings.xml` and update translations; run `python3 scripts/check_localization.py`. See [translation guidance](docs/TRANSLATING.md).
- Follow `.editorconfig`; keep changes focused and explain user-visible behavior in the pull request.

Release signing is not required for contributions. See [release preparation](docs/RELEASING.md) for maintainer builds.
