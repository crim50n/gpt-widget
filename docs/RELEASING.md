# Signed releases

## Signing configuration

The release key is never committed. The module accepts the same property names used by the maintainer's other Android projects.

For a local keystore, copy `app/keystore/signing.properties.example` to the ignored `app/keystore/signing.properties` and configure:

| Property | Meaning |
| --- | --- |
| `keystore` | Absolute path, or a path relative to `app/` |
| `keystore.alias` | Private-key alias |
| `keystore.password` | Keystore password; also the default key password |
| `key.password` | Optional separate private-key password |

Alternatively use environment variables `KEYSTORE_FILE`, `KEYSTORE_ALIAS`, `KEYSTORE_PASSWORD`, and optional `KEY_PASSWORD`. Environment variables take precedence.

An existing private properties file can be loaded without copying it into this project:

```sh
KEYSTORE_FILE=/absolute/path/to/release.keystore \
  ./gradlew -PsigningPropertiesFile=/absolute/path/to/signing.properties \
  :app:packageReleaseArtifacts
```

Use an absolute `KEYSTORE_FILE` with external properties files so relative paths are unambiguous. Do not put passwords directly in command-line arguments or commit the real properties file.

## Local build

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:packageReleaseArtifacts
```

The release uses R8 and resource shrinking. It fails if signing configuration is missing; it does not silently substitute a debug key.

Outputs:

- `release-apks/chatgpt-usage-<version>.apk`
- `release-apks/chatgpt-usage-<version>.apk.sha256`

Verify the APK using SDK Build Tools 35.0.0:

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose release-apks/chatgpt-usage-<version>.apk
```

For checksum verification, run `sha256sum --check` on the `.sha256` file from inside `release-apks/`.

## GitHub setup

Create the repository, push the reviewed source tree to `main`, and enable Actions. Configure these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_ALIAS` | Private-key alias |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Optional separate key password |

Store the keystore and recovery details securely outside GitHub as well. All future releases must use the same signing key.

### CI

`ci.yml` runs debug builds, unit tests and lint on `main`, pull requests, and manual dispatch. It does not need signing secrets. Debug APKs and reports are retained as workflow artifacts.

### Publication

1. Update the literal `versionName` and monotonically increasing `versionCode` in `app/build.gradle`.
2. Add `release-notes/<version>.md` and update user documentation.
3. Run local checks and inspect the release APK on a device.
   Run `python3 scripts/check_publication.py` before staging to check the Git-visible file set for private/generated material.
4. Commit reviewed changes and push an annotated `v<version>` tag.

`release.yml` checks that the existing tag matches `versionName`, runs tests/lint, decodes the signing key into the runner's temporary directory, builds the signed APK, verifies its signature and checksum, then publishes a GitHub Release. Manual dispatch must select the version tag, not a branch. Existing release assets are not overwritten.

The workflow uses its built-in `GITHUB_TOKEN`; no personal access token is required for release publication. Signing material is removed in an `always()` cleanup step.

## Development-build migration

Release application ID: `dev.local.usagewidget`. Current debug ID: `dev.local.usagewidget.debug`.

Early APKs used the release ID with a debug signature. Android cannot update them to a differently signed release. Remove that old development installation before installing the signed release and complete sign-in again. App-private credentials are not exported or transferred between packages.
