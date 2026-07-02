package io.pulsekit.android.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import io.pulsekit.runtime.Pulse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recomposition **heatmap** overlay. Add it to any composable you want to watch:
 *
 * ```
 * Text("Cart total", Modifier.pulseRecomposeHeatmap("cart-total"))
 * ```
 *
 * It draws a border that heats up (blue → red) the more that composable recomposes,
 * and cools back down after ~1.5 s of inactivity — so recomposition hotspots are
 * obvious at a glance. Each recomposition is also reported to [Pulse.recordRecomposition]
 * under [tag], surfacing counts in the dashboard's **Recompositions** panel.
 *
 * Runtime limitation: recompositions can only be counted where this modifier is
 * applied (there's no runtime hook for *every* composable — that needs Compose
 * compiler metrics), so it is opt-in per composable.
 */
fun Modifier.pulseRecomposeHeatmap(tag: String): Modifier =
    this then RecomposeHeatmapElement(tag)

private data class RecomposeHeatmapElement(val tag: String) :
    ModifierNodeElement<RecomposeHeatmapNode>() {

    override fun create(): RecomposeHeatmapNode = RecomposeHeatmapNode(tag)

    override fun update(node: RecomposeHeatmapNode) {
        node.onRecompose()
    }

    // Force Compose to treat the element as "changed" on every recomposition so
    // update() (our counter) runs each time. This is the recompose-highlighter trick.
    override fun equals(other: Any?): Boolean = false
    override fun hashCode(): Int = tag.hashCode()
}

private class RecomposeHeatmapNode(private val tag: String) : Modifier.Node(), DrawModifierNode {

    private var heat = 0L
    private var coolJob: Job? = null

    fun onRecompose() {
        heat++
        Pulse.recordRecomposition(tag, System.currentTimeMillis())
        invalidateDraw()
        coolJob?.cancel()
        coolJob = coroutineScope.launch {
            delay(COOLDOWN_MS)
            heat = 0
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val color = heatColor(heat) ?: return
        val widthPx = (1.5f + heat.coerceAtMost(HEAT_CAP) * 0.4f).dp.toPx()
        val inset = widthPx / 2f
        drawRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - widthPx, size.height - widthPx),
            style = Stroke(width = widthPx),
        )
    }

    private fun heatColor(count: Long): Color? = when {
        count <= 1 -> null // the first composition is not a "recomposition"
        else -> {
            val t = (count - 2).coerceIn(0, HEAT_CAP).toFloat() / HEAT_CAP.toFloat()
            lerp(Cool, Hot, t)
        }
    }

    private companion object {
        const val COOLDOWN_MS = 1500L
        const val HEAT_CAP = 20L
        val Cool = Color(0xFF2962FF)
        val Hot = Color(0xFFD50000)
    }
}
