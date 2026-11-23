# Translation providers

The `TranslationProvider` enum has `INDEX_TRANSLATE`, `BAIDU`, `GOOGLE`, `YOUDAO`, `LIBRE_TRANSLATE`, `DEEPL` and `MICROSOFT`. Each provider's settings are stored through `TweaksRepository` (KSafe-encrypted), and `TranslationRepositoryImpl.resolveTranslator()` picks the implementation.

- **Index-Translate** uses Bilibili's free public OpenAI-compatible endpoint, no key. It posts one chat completion per chunk and sends the required `User-Agent`.
- **OpenAI-compatible** takes a base URL, model and optional key, so it covers Zhipu GLM, OpenAI and local proxies. The endpoint is the base URL plus `/chat/completions` unless the full URL is entered.
- **Baidu** uses the general text translation endpoint with AppID + key and the MD5 `appid+q+salt+secret` signature.
- **LibreTranslate** uses the bundled `translate.disroot.org` mirror when the user's URL setting is blank.
- **DeepL** sends keys ending in `:fx` to `api-free.deepl.com`.
- **Microsoft** uses No-Trace by default: text is never stored and never used for training.

