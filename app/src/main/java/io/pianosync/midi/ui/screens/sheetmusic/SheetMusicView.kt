package io.pianosync.midi.ui.screens.sheetmusic

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SheetMusicView(
    viewModel: SheetMusicViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit = {}
) {
    val context = LocalContext.current
    viewModel.initIfNeeded(context)

    SvgWebView(
        svgContent = viewModel.svgString.value,
        onSizeChanged = { size ->
            viewModel.onSize(size)
            onSizeChanged(size)
        },
        modifier = modifier
    )
}

@Composable
fun SvgWebView(
    svgContent: String,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val html = """
        <!DOCTYPE html>
        <html>
        <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0; background-color: white;">
        $svgContent
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onSizeChanged(size)
            }
    )
}
