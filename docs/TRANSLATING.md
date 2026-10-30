# Translating the app

English is the base language in `app/src/main/res/values/strings.xml`. Russian is in `values-ru/strings.xml`. Android selects the matching translation and falls back to English for other locales. On Android 13+ the app exposes its supported languages in Android's per-app language settings.

To add a language:

1. Create `app/src/main/res/values-<language>/strings.xml` using the English catalog as the source.
2. Translate every entry except those marked `translatable="false"`.
3. Preserve keys, array order, positional placeholders such as `%1$s` and `%2$d`, escaped newlines (`\n`) and literal percentage escapes (`%%`).
4. Add the locale to `app/src/main/res/xml/locales_config.xml`.
5. Run `python3 scripts/check_localization.py` and the Android checks described in [development](DEVELOPMENT.md).

Date examples, month names and weekdays follow the app locale. Formats with numeric month/day order are explicitly labeled. Known reset types are localized by the app; unknown server-provided titles are shown as received.

Keep short widget labels concise. Check 1×1, narrow columns and horizontal strips with enlarged fonts. The UI tests use sample data and do not authorize a real account.

New UI text belongs in resources rather than Java string literals. Background components use `UiText.t(...)`, initialized by `UsageApplication`, so errors and status messages are also translated.
