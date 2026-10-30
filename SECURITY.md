# Security and privacy

## Reporting

Use GitHub private vulnerability reporting if it is enabled for this repository. Otherwise contact the maintainer before sharing sensitive details. Do not post live credentials or a full network capture in a public issue.

## Data handling

- Authentication uses a one-time device code on OpenAI's authorization page, opened in the system browser.
- The app does not collect passwords, read browser cookies, scrape HTML, or access ChatGPT conversation history.
- Access/refresh tokens and unfinished sign-in attempts are encrypted with AES-GCM and Android Keystore keys.
- Account caches stay in the app's private storage. Cloud backup and device transfer are excluded.
- An unfinished reset request retains its request ID so a retry can use the same idempotency value. This record is bound to a hashed account identifier and is separate from the active login cache.
- There is no analytics SDK or project-operated backend. Network requests go to OpenAI services.
- Screenshots are allowed. Hide account details and one-time codes before sharing them.

The application uses internal ChatGPT endpoints and the Codex OAuth client flow. Provider-side changes may affect access. It is an independent project, not an OpenAI product.

## Release keys

Private signing keys and passwords must stay outside Git. CI reads signing material from repository secrets and removes the temporary keystore after the build. Published APK certificates are public; their private keys are not.
