package io.pulsekit.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pulsekit.core.FpsSnapshot
import io.pulsekit.core.MemorySample
import io.pulsekit.core.StartupMetric
import io.pulsekit.core.StartupPhase
import java.util.Locale

// ---------------------------------------------------------------------------
// FPS / Jank
// ---------------------------------------------------------------------------

@Composable
fun FpsScreen(snapshot: FpsSnapshot, onBack: () -> Unit) {
    MetricScaffold(title = "FPS / Jank", onBack = onBack) {
        BigMetric(value = snapshot.fps.toString(), unit = "fps", caption = "avg ${snapshot.averageFps} fps")
        if (snapshot.recentFrameMs.isNotEmpty()) {
            SectionLabel("Frame time (ms)")
            Sparkline(
                values = snapshot.recentFrameMs.map { it.toFloat() },
                thresholdValue = 1000f / 60f, // 16.67 ms budget
            )
        }
        SectionLabel("Details")
        MetricCard {
            StatRow("Average FPS", "${snapshot.averageFps}")
            Divider()
            StatRow("Worst frame", format1(snapshot.worstFrameMs) + " ms")
            Divider()
            StatRow("Jank", "${snapshot.jankPercent}%")
            Divider()
            StatRow("Dropped frames", "${snapshot.droppedFrames}")
            Divider()
            StatRow("Samples", "${snapshot.sampleCount}")
        }
    }
}

// ---------------------------------------------------------------------------
// Memory
// ---------------------------------------------------------------------------

@Composable
fun MemoryScreen(samples: List<MemorySample>, onBack: () -> Unit) {
    MetricScaffold(title = "Memory", onBack = onBack) {
        val latest = samples.lastOrNull()
        if (latest == null) {
            Text(
                "Sampling…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricScaffold
        }
        BigMetric(value = format1(latest.usedBytes.toMb()), unit = "MB", caption = "of ${format1(latest.maxBytes.toMb())} MB heap")
        SectionLabel("Used heap (MB)")
        Sparkline(values = samples.map { it.usedBytes.toMb().toFloat() })
        SectionLabel("Details")
        val peak = samples.maxOf { it.usedBytes }
        MetricCard {
            StatRow("Used", format1(latest.usedBytes.toMb()) + " MB")
            Divider()
            StatRow("Native heap", format1(latest.nativeBytes.toMb()) + " MB")
            Divider()
            StatRow("Max / limit", format1(latest.maxBytes.toMb()) + " MB")
            Divider()
            StatRow("Peak used", format1(peak.toMb()) + " MB")
            Divider()
            StatRow("Samples", "${samples.size}")
        }
    }
}

// ---------------------------------------------------------------------------
// Startup
// ---------------------------------------------------------------------------

@Composable
fun StartupScreen(metric: StartupMetric?, onBack: () -> Unit) {
    MetricScaffold(title = "Startup", onBack = onBack) {
        if (metric == null) {
            Text(
                "Startup timeline not captured yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricScaffold
        }
        BigMetric(value = "${metric.totalMs}", unit = "ms", caption = "time to first frame")
        SectionLabel("Waterfall")
        val total = metric.totalMs.coerceAtLeast(1)
        MetricCard {
            metric.phases.forEachIndexed { index, phase ->
                WaterfallRow(phase = phase, totalMs = total)
                if (index != metric.phases.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WaterfallRow(phase: StartupPhase, totalMs: Long) {
    val fraction = (phase.durationMs.toFloat() / totalMs).coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                phase.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${phase.durationMs} ms",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun MetricScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PanelTopBar(title = title, onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            content = content,
        )
    }
}

@Composable
private fun BigMetric(value: String, unit: String, caption: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.padding(2.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Text(
        caption,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun MetricCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A minimal single-series line chart with an optional dashed threshold line. */
@Composable
private fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    thresholdValue: Float? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val thresholdColor = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .padding(12.dp),
    ) {
        if (values.size < 2) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val min = values.min()
            val max = values.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1)
            fun y(v: Float) = size.height - ((v - min) / range) * size.height

            thresholdValue?.let { t ->
                if (t in min..max) {
                    val ty = y(t)
                    drawLine(
                        color = thresholdColor.copy(alpha = 0.6f),
                        start = Offset(0f, ty),
                        end = Offset(size.width, ty),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
            }

            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val yy = y(v)
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3f))
        }
    }
}

private fun Long.toMb(): Double = this / 1024.0 / 1024.0
private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
