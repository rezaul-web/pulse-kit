package io.pulsekit.android.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pulsekit.core.NetworkHeader
import io.pulsekit.core.NetworkTransaction
import io.pulsekit.core.toCurl
import kotlinx.serialization.json.Json

/** Colors for HTTP status / failure state, readable in light and dark. */
private object ApiColors {
    val Success = Color(0xFF2E7D32)
    val Redirect = Color(0xFFF9A825)
    val Error = Color(0xFFC62828)
}

private val PrettyJson = Json { prettyPrint = true }

// ---------------------------------------------------------------------------
// List screen
// ---------------------------------------------------------------------------

/** The **API Requests** panel: a list of captured HTTP calls, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiRequestsListScreen(
    transactions: List<NetworkTransaction>,
    onOpen: (NetworkTransaction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ApiTopBar(title = "API Requests", onBack = onBack) },
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyRequests(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(transactions, key = { it.id }) { txn ->
                    RequestRow(txn = txn, onClick = { onOpen(txn) })
                }
            }
        }
    }
}

@Composable
private fun RequestRow(txn: NetworkTransaction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(txn)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${txn.method}  ${txn.path}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${txn.host} · ${txn.durationMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small colored pill showing the status code (or ERR for a failed call). */
@Composable
private fun StatusPill(txn: NetworkTransaction) {
    val (color, label) = when {
        txn.isFailed -> ApiColors.Error to "ERR"
        txn.statusCode >= 400 -> ApiColors.Error to txn.statusCode.toString()
        txn.statusCode >= 300 -> ApiColors.Redirect to txn.statusCode.toString()
        txn.statusCode >= 200 -> ApiColors.Success to txn.statusCode.toString()
        else -> MaterialTheme.colorScheme.outline to "—"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyRequests(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No requests captured yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add PulseOkHttpInterceptor() to your OkHttpClient, then make a call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Detail screen (tabs: cURL · Request · Response)
// ---------------------------------------------------------------------------

private val TABS = listOf("cURL", "Request", "Response")

/** Tabbed detail for one captured request. Each tab is tap/long-press to copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTransactionScreen(txn: NetworkTransaction, onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val copy = rememberCopyAction()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ApiTopBar(
                title = "${txn.method}  ${txn.path}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { copy(txn.toCurl()) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Copy cURL")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SummaryLine(txn)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> CopyableTab(copyText = txn.toCurl()) {
                    CodeBlock(txn.toCurl())
                }
                1 -> CopyableTab(copyText = requestText(txn)) {
                    RequestSections(txn)
                }
                else -> CopyableTab(copyText = responseText(txn)) {
                    ResponseSections(txn)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(txn: NetworkTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(txn)
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (txn.isFailed) "Failed · ${txn.durationMs} ms" else "${txn.protocol} · ${txn.durationMs} ms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Request tab: url, headers, then the formatted request body. */
@Composable
private fun ColumnScope.RequestSections(txn: NetworkTransaction) {
    SectionLabel("URL")
    MonoText(txn.url)
    txn.error?.let {
        SectionLabel("Error")
        MonoText(it, color = ApiColors.Error)
    }
    SectionLabel("Headers")
    Headers(txn.requestHeaders)
    SectionLabel("Body")
    val body = txn.requestBody
    if (body.isNullOrEmpty()) EmptyValue("No request body") else CodeContent(prettyBody(body, txn.requestContentType))
}

/** Response tab: the response DATA (body) first, then status and headers. */
@Composable
private fun ColumnScope.ResponseSections(txn: NetworkTransaction) {
    SectionLabel("Response data")
    val body = txn.responseBody
    if (body.isNullOrEmpty()) EmptyValue("No response body") else CodeContent(prettyBody(body, txn.responseContentType))
    SectionLabel("Status")
    MonoText(if (txn.isFailed) "Failed: ${txn.error}" else "${txn.statusCode} ${txn.responseMessage}")
    SectionLabel("Headers")
    Headers(txn.responseHeaders)
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = { actions() },
    )
}

/**
 * Scrollable tab body that copies its whole [copyText] on tap **or** long-press,
 * with a confirmation toast. A hint row makes the affordance discoverable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableTab(copyText: String, content: @Composable ColumnScope.() -> Unit) {
    val copy = rememberCopyAction()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .combinedClickable(
                onClick = { copy(copyText) },
                onLongClick = { copy(copyText) },
            )
            .padding(16.dp),
    ) {
        CopyHint()
        content()
    }
}

@Composable
private fun CopyHint() {
    Text(
        text = "Tap or long-press to copy",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Headers(headers: List<NetworkHeader>) {
    if (headers.isEmpty()) {
        EmptyValue("None")
        return
    }
    Column {
        headers.forEach { header ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = "${header.name}: ",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = header.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MonoText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}

@Composable
private fun EmptyValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** A monospace code block on a surfaceVariant background; long lines wrap. */
@Composable
private fun ColumnScope.CodeBlock(text: String) {
    CodeContent(text)
}

@Composable
private fun CodeContent(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Formatting & clipboard helpers
// ---------------------------------------------------------------------------

/** Returns a `copy(text)` action that writes to the clipboard and toasts. */
@Composable
private fun rememberCopyAction(): (String) -> Unit {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val context: Context = LocalContext.current
    return remember(clipboard, context) {
        { text: String ->
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Whole-tab plain text for the Request tab (for copy). */
private fun requestText(txn: NetworkTransaction): String = buildString {
    appendLine("${txn.method} ${txn.url}")
    txn.error?.let { appendLine("Error: $it") }
    if (txn.requestHeaders.isNotEmpty()) {
        appendLine()
        txn.requestHeaders.forEach { appendLine("${it.name}: ${it.value}") }
    }
    val body = txn.requestBody
    if (!body.isNullOrEmpty()) {
        appendLine()
        appendLine(prettyBody(body, txn.requestContentType))
    }
}.trim()

/** Whole-tab plain text for the Response tab (data first, for copy). */
private fun responseText(txn: NetworkTransaction): String = buildString {
    val body = txn.responseBody
    if (!body.isNullOrEmpty()) {
        appendLine(prettyBody(body, txn.responseContentType))
        appendLine()
    }
    appendLine(if (txn.isFailed) "Failed: ${txn.error}" else "Status: ${txn.statusCode} ${txn.responseMessage}")
    if (txn.responseHeaders.isNotEmpty()) {
        appendLine()
        txn.responseHeaders.forEach { appendLine("${it.name}: ${it.value}") }
    }
}.trim()

/** Pretty-print a JSON body; return the original text for anything else. */
private fun prettyBody(body: String, contentType: String?): String {
    val looksJson = contentType?.contains("json", ignoreCase = true) == true ||
        body.trimStart().firstOrNull()?.let { it == '{' || it == '[' } == true
    if (!looksJson) return body
    return try {
        PrettyJson.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            PrettyJson.parseToJsonElement(body),
        )
    } catch (e: Exception) {
        body
    }
}
