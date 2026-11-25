package com.example.executorchllamademo.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private const val TAG = "AdsoOrb"

/** What AdSo's orb is doing. Matches the states of the EnergyOrb web component. */
enum class OrbState(val js: String) {
    IDLE("idle"),
    THINKING("thinking"),
    GENERATING("generating"),
    RESTING("resting"), // fades down, then returns to idle by itself
}

/**
 * AdSo's animated 3D energy orb: the React Three Fiber component from LlamaDemo/orb,
 * built into assets/orb/orb.js and shown in a transparent WebView. Works offline.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EnergyOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.semantics { contentDescription = "AdSo AI, ${state.js}" },
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d(TAG, "${message.messageLevel()} ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        loaded = true
                        Log.d(TAG, "orb page loaded: $url")
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.w(TAG, "orb load error ${error?.errorCode} ${error?.description} for ${request?.url}")
                    }

                    // The orb page never navigates anywhere.
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                }
                loadUrl("file:///android_asset/orb/index.html#state=${state.js}&intensity=$intensity")
                webView = this
            }
        },
        onRelease = { it.destroy() },
    )

    // Send state changes to the page. Retries briefly in case React has not mounted yet.
    LaunchedEffect(state, intensity, loaded) {
        if (!loaded) return@LaunchedEffect
        webView?.evaluateJavascript(
            "(function f(n){if(window.AdsoOrb){AdsoOrb.setState('${state.js}');AdsoOrb.setIntensity($intensity)}" +
                "else if(n>0){setTimeout(function(){f(n-1)},100)}})(30)",
            null,
        )
    }

    // Stop rendering while the app is in the background, to save battery.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
