# Architecture

## Overview

One native Android application module, written in Java 17, with no third-party runtime dependencies. Android platform widgets provide the UI and `RemoteViews` home-screen widgets. `JobScheduler` manages background refreshes.

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Usage overview, reset activation, widget previews and preferences |
| `BrowserLoginActivity` | Device-code sign-in screen and resumable authorization controller |
| `OAuthClient` | Codex device authorization, code exchange and token refresh |
| `AuthSession` | Account validation, serialized refresh and one read-only retry after 401 |
| `Store` | Encrypted active credentials and sanitized usage cache |
| `PendingLoginStore` | Encrypted in-progress authorization, separate from active credentials |
| `Api`, `Models` | API transport and explicit JSON parsing |
| `Refresh`, `RefreshJob` | Manual/background refresh and loading state |
| `Redemption`, `RedemptionStore`, `ResetService` | Reset confirmation, persistent request IDs and result handling |
| `UsageWidget`, `WidgetSizing` | Adaptive layouts and loading indicators |
| `Appearance`, `AppDialogs`, `AppNotice` | Shared themes, dialogs and in-app notifications |

## Sign-in

1. Request a device code from `auth.openai.com` using the public Codex client ID.
2. Open the fixed verification page in the system browser.
3. Poll at the server-supplied interval, with a 15-minute deadline.
4. Exchange the approved authorization code and PKCE verifier for tokens.
5. Read usage and reset details with the candidate access token. Confirm the account ID from the authenticated usage response.
6. Atomically activate the new credentials and snapshots only if the original connection revision is still current.

JWT claims are routing hints, not the source of account authorization. Candidate tokens never replace the active session before both reads succeed. Pending requests survive Activity recreation and process restarts. An interrupted one-time code exchange requires a new code when its outcome cannot be determined.

The OAuth implementation follows the Codex legacy device flow. It is distinct from dynamic-client Sign in with ChatGPT for the public Responses API. The app does not import another application's credential files, extract browser cookies, or scrape web pages.

## API requests

All account requests use HTTPS, Bearer authentication and an account context. Redirect following is disabled.

| Method | Path under `https://chatgpt.com/backend-api` | Purpose |
| --- | --- | --- |
| GET | `/wham/usage` | Usage windows, credits and account identity |
| GET | `/wham/rate-limit-reset-credits` | Available reset tickets |
| POST | `/wham/rate-limit-reset-credits/consume` | Apply the selected reset |

Windows are classified by their duration, not by assuming primary always means five hours. Unix reset timestamps and ISO ticket expiration timestamps are converted to the device's timezone. Unknown/missing values are not replaced with a fabricated zero or full allowance.

## Reset requests

The app confirms the selected reset, fetches the current list, and persists `credit_id` plus a generated `redeem_request_id` before sending the POST. An uncertain request can only be retried with the same pair. The pending record is account-bound and survives reauthentication.

`reset` is success. `already_redeemed` is success only for a saved retry. `no_credit` and `nothing_to_reset` are definitive rejections. Unknown responses retain the pending attempt. POSTs are never automatically repeated by the refresh machinery.

## Background work

Configured refresh intervals are 15 minutes, 30 minutes, 1 hour, 3 hours, or 6 hours; manual mode cancels periodic network work. Android may defer jobs. Launcher updates redraw cached data without bypassing the interval setting. Access-token refresh is serialized and replacement tokens are stored together; stale results cannot overwrite a newly selected account.

## Upstream references

- [Codex authentication](https://developers.openai.com/codex/auth)
- [Device authorization implementation](https://github.com/openai/codex/blob/4d3ed3f3b47d39f8fe0b60df74a20f528b6fc618/codex-rs/login/src/device_code_auth.rs)
- [Usage and reset client](https://github.com/openai/codex/blob/4d3ed3f3b47d39f8fe0b60df74a20f528b6fc618/codex-rs/backend-client/src/client/rate_limit_resets.rs)

These references describe the researched implementation, not a public stability guarantee for internal endpoints.
