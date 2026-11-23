# Translation providers

The `TranslationProvider` enum has `INDEX_TRANSLATE`, `OPENAI_COMPATIBLE`, `BAIDU`, `GOOGLE`, `YOUDAO`, `LIBRE_TRANSLATE`, `DEEPL` and `MICROSOFT`. Each provider's settings are stored through `TweaksRepository` (KSafe-encrypted), and `TranslationRepositoryImpl.resolveTranslator()` picks the implementation.

- **Index-Translate** uses Bilibili's free public OpenAI-compatible endpoint, no key. It streams SSE and sends the required `User-Agent`. A long README can take longer than the gateway's 10-second non-streaming limit, so the client caps a request at 60 seconds and chunks at 2,000 characters.
- **OpenAI-compatible** takes a base URL, model, optional key and a prompt template. An empty base URL uses `https://api.openai.com/v1`; the endpoint is the base URL plus `/chat/completions` unless the full URL is entered. It also reads `data[].id` from `GET {base}/models` for the model picker. The prompt template supports `{target}`, `{source}` and `{text}`, and the source text is appended when `{text}` is omitted. Responses may be streaming SSE or a complete JSON body; null or non-JSON `data:` frames are skipped.
- **Baidu** uses the general text translation endpoint with AppID + key and the MD5 `appid+q+salt+secret` signature. The free tier allows one request per second, so requests are serialized and spaced; this is separate from the general translation cache.
- **LibreTranslate** uses the bundled `translate.disroot.org` mirror when the user's URL setting is blank.
- **DeepL** sends keys ending in `:fx` to `api-free.deepl.com`.
- **Microsoft** uses No-Trace by default: text is never stored and never used for training.
