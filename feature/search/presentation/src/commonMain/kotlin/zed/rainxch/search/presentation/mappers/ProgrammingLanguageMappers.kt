package zed.rainxch.search.presentation.mappers

import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.ProgrammingLanguage.*
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi

fun ProgrammingLanguageUi.toDomain(): ProgrammingLanguage =
    when (this) {
        ProgrammingLanguageUi.All -> All
        ProgrammingLanguageUi.Kotlin -> Kotlin
        ProgrammingLanguageUi.Java -> Java
        ProgrammingLanguageUi.JavaScript -> JavaScript
        ProgrammingLanguageUi.TypeScript -> TypeScript
        ProgrammingLanguageUi.Python -> Python
        ProgrammingLanguageUi.Swift -> Swift
        ProgrammingLanguageUi.Rust -> Rust
        ProgrammingLanguageUi.Go -> Go
        ProgrammingLanguageUi.CSharp -> CSharp
        ProgrammingLanguageUi.CPlusPlus -> CPlusPlus
        ProgrammingLanguageUi.C -> C
        ProgrammingLanguageUi.Dart -> Dart
        ProgrammingLanguageUi.Ruby -> Ruby
        ProgrammingLanguageUi.PHP -> PHP
    }
