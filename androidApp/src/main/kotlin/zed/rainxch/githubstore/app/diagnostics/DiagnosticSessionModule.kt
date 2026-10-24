package zed.rainxch.githubstore.app.diagnostics

import org.koin.dsl.module
import zed.rainxch.core.domain.diagnostics.DiagnosticSessionController

val diagnosticSessionModule =
    module {
        single<DiagnosticSessionController> {
            AndroidDiagnosticSessionController(get())
        }
    }
