package zed.rainxch.core.presentation.components.shapes

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

// A Morph at one point of its progress. A spring can overshoot, so the outline itself stays
// between the two shapes.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        return Outline.Generic(path)
    }
}
