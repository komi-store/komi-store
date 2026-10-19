# Translation providers

The `TranslationProvider` enum has `GOOGLE`, `YOUDAO`, `LIBRE_TRANSLATE`, `DEEPL` and `MICROSOFT`. Each provider's settings are stored through `TweaksRepository` (KSafe-encrypted), and `TranslationRepositoryImpl.resolveTranslator()` picks the implementation.

- **LibreTranslate** uses the bundled `translate.disroot.org` mirror when the user's URL setting is blank.
- **DeepL** sends keys ending in `:fx` to `api-free.deepl.com`.
- **Microsoft** uses No-Trace by default: text is never stored and never used for training.
