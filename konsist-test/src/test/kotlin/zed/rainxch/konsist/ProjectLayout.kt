package zed.rainxch.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

enum class Layer { Domain, Data, Presentation }

private val MODULE = Regex("""^(core|feature/([^/]+))/(domain|data|presentation)/""")

val KoFileDeclaration.relativePath: String
    get() = projectPath.replace('\\', '/').trimStart('/')

val KoFileDeclaration.layer: Layer?
    get() =
        MODULE.find(relativePath)?.groupValues?.get(3)?.let { name ->
            Layer.entries.first { it.name.equals(name, ignoreCase = true) }
        }

val KoFileDeclaration.expectedPackagePrefix: String?
    get() {
        val match = MODULE.find(relativePath) ?: return null
        val owner = match.groupValues[2].ifEmpty { match.groupValues[1] }.replace("-", "")
        return "zed.rainxch.$owner.${match.groupValues[3]}"
    }

private val APP_ROOTS = Regex("""^(core|feature|composeApp|androidApp)/""")

val appProduction: KoScope by lazy {
    Konsist.scopeFromProduction().slice { APP_ROOTS.containsMatchIn(it.relativePath) }
}
