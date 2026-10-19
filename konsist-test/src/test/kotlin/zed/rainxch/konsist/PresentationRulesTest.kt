package zed.rainxch.konsist

import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class PresentationRulesTest {
    private val production = appProduction

    @Test
    fun view_models_are_named_after_what_they_are() {
        production
            .classes()
            .filter { it.hasParentWithName("ViewModel") }
            .assertTrue { it.name.endsWith("ViewModel") }
    }

    @Test
    fun view_models_live_in_presentation_or_app_modules() {
        production
            .classes()
            .filter { it.hasParentWithName("ViewModel") }
            .assertTrue {
                it.containingFile.layer == Layer.Presentation ||
                    it.containingFile.relativePath.startsWith("composeApp/")
            }
    }

    @Test
    fun actions_and_events_are_sealed() {
        val isActionOrEvent = { name: String -> name.endsWith("Action") || name.endsWith("Event") }
        production
            .interfaces()
            .filter { it.isTopLevel && it.containingFile.layer == Layer.Presentation && isActionOrEvent(it.name) }
            .assertTrue { it.hasSealedModifier }
        production
            .classes()
            .filter { it.isTopLevel && it.containingFile.layer == Layer.Presentation && isActionOrEvent(it.name) }
            .assertTrue { it.hasSealedModifier }
    }
}
