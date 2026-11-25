package zed.rainxch.search.presentation.model

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.language_all
import zed.rainxch.githubstore.core.presentation.res.language_c
import zed.rainxch.githubstore.core.presentation.res.language_cpp
import zed.rainxch.githubstore.core.presentation.res.language_csharp
import zed.rainxch.githubstore.core.presentation.res.language_dart
import zed.rainxch.githubstore.core.presentation.res.language_go
import zed.rainxch.githubstore.core.presentation.res.language_java
import zed.rainxch.githubstore.core.presentation.res.language_javascript
import zed.rainxch.githubstore.core.presentation.res.language_kotlin
import zed.rainxch.githubstore.core.presentation.res.language_php
import zed.rainxch.githubstore.core.presentation.res.language_python
import zed.rainxch.githubstore.core.presentation.res.language_ruby
import zed.rainxch.githubstore.core.presentation.res.language_rust
import zed.rainxch.githubstore.core.presentation.res.language_swift
import zed.rainxch.githubstore.core.presentation.res.language_typescript
import zed.rainxch.search.domain.model.ProgrammingLanguage

enum class ProgrammingLanguageUi(
    val domain: ProgrammingLanguage,
    val label: StringResource,
) {
    All(ProgrammingLanguage.All, Res.string.language_all),
    Kotlin(ProgrammingLanguage.Kotlin, Res.string.language_kotlin),
    Java(ProgrammingLanguage.Java, Res.string.language_java),
    JavaScript(ProgrammingLanguage.JavaScript, Res.string.language_javascript),
    TypeScript(ProgrammingLanguage.TypeScript, Res.string.language_typescript),
    Python(ProgrammingLanguage.Python, Res.string.language_python),
    Swift(ProgrammingLanguage.Swift, Res.string.language_swift),
    Rust(ProgrammingLanguage.Rust, Res.string.language_rust),
    Go(ProgrammingLanguage.Go, Res.string.language_go),
    CSharp(ProgrammingLanguage.CSharp, Res.string.language_csharp),
    CPlusPlus(ProgrammingLanguage.CPlusPlus, Res.string.language_cpp),
    C(ProgrammingLanguage.C, Res.string.language_c),
    Dart(ProgrammingLanguage.Dart, Res.string.language_dart),
    Ruby(ProgrammingLanguage.Ruby, Res.string.language_ruby),
    PHP(ProgrammingLanguage.PHP, Res.string.language_php),
}
