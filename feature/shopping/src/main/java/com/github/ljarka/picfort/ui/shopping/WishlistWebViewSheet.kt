package com.github.ljarka.picfort.ui.shopping

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.ljarka.picfort.core.model.WishlistPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * In-app WebView bottom sheet used when server-side link preview cannot fetch an image
 * (for example due to anti-bot / CAPTCHA challenges). The user can complete any verification
 * in the visible WebView, and once the product page loads, metadata is extracted in-page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WishlistWebViewSheet(url: String, onExtracted: (WishlistPreview) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var extractedOnce by remember { mutableStateOf(false) }

    fun tryExtract(view: WebView, force: Boolean = false) {
        if (extractedOnce && !force) return
        view.evaluateJavascript(EXTRACT_PREVIEW_JS) { raw ->
            val preview = parseWebViewPreviewJson(raw)
            if (preview != null && !preview.imageUrl.isNullOrBlank()) {
                if (!extractedOnce) {
                    extractedOnce = true
                    onExtracted(preview)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close browser")
                }
                Text(
                    text = "Confirm in browser",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { webViewRef?.let { tryExtract(it, force = true) } },
                ) {
                    Text("Grab photo")
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                val wv = view ?: return
                                tryExtract(wv)
                                // Re-check shortly after load in case SPA / post-challenge hydration updates meta/images.
                                scope.launch {
                                    delay(1000)
                                    if (!extractedOnce) tryExtract(wv)
                                    delay(1500)
                                    if (!extractedOnce) tryExtract(wv)
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl(url)
                    }
                },
            )
        }
    }
}

internal fun parseWebViewPreviewJson(raw: String?): WishlistPreview? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        val outer = Json.parseToJsonElement(raw)
        val obj = when (outer) {
            is kotlinx.serialization.json.JsonPrimitive -> Json.parseToJsonElement(outer.content).jsonObject
            else -> outer.jsonObject
        }
        val imageUrl = obj["imageUrl"]?.jsonPrimitive?.content?.trim()?.takeIf { isHttpUrl(it) }
        val title = obj["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val priceRaw = obj["price"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val priceMinor = priceRaw?.let { parseTotalMinor(it) }
        if (imageUrl == null && title == null && priceMinor == null) {
            null
        } else {
            WishlistPreview(
                title = title,
                imageUrl = imageUrl,
                priceMinor = priceMinor,
            )
        }
    }.getOrNull()
}

private val EXTRACT_PREVIEW_JS = """
(function() {
  try {
    var html = document.documentElement ? document.documentElement.innerHTML : '';
    if (html.indexOf('captcha-delivery.com') !== -1 || html.indexOf('cf-challenge') !== -1) {
      return null;
    }
    function meta(names) {
      for (var i = 0; i < names.length; i++) {
        var el = document.querySelector('meta[property="' + names[i] + '"], meta[name="' + names[i] + '"]');
        if (el && el.getAttribute('content')) {
          var v = el.getAttribute('content').trim();
          if (v) return v;
        }
      }
      return null;
    }
    var ldTitle = null, ldImage = null, ldPrice = null;
    function walkLd(node) {
      if (!node || typeof node !== 'object') return;
      if (Array.isArray(node)) { node.forEach(walkLd); return; }
      var t = node['@type'];
      var isProd = t === 'Product' || (Array.isArray(t) && t.indexOf('Product') !== -1);
      if (isProd) {
        if (!ldTitle && typeof node.name === 'string') ldTitle = node.name;
        if (!ldImage) {
          var img = Array.isArray(node.image) ? node.image[0] : node.image;
          if (typeof img === 'string') ldImage = img;
          else if (img && typeof img.url === 'string') ldImage = img.url;
        }
        if (!ldPrice && node.offers) {
          var off = Array.isArray(node.offers) ? node.offers[0] : node.offers;
          if (off && (off.price !== undefined || off.lowPrice !== undefined)) {
            ldPrice = String(off.price !== undefined ? off.price : off.lowPrice);
          }
        }
        return;
      }
      if (node['@graph']) walkLd(node['@graph']);
    }
    var scripts = document.querySelectorAll('script[type*="ld+json"]');
    for (var s = 0; s < scripts.length; s++) {
      try { walkLd(JSON.parse(scripts[s].textContent)); } catch (e) {}
    }
    var imageUrl = meta(['og:image', 'og:image:url', 'twitter:image']) || ldImage;
    if (!imageUrl) {
      var imgs = document.querySelectorAll('img');
      var bestArea = 0;
      for (var j = 0; j < imgs.length; j++) {
        var im = imgs[j];
        var src = im.currentSrc || im.src || '';
        if (src.indexOf('http') !== 0) continue;
        var area = (im.naturalWidth || 0) * (im.naturalHeight || 0);
        if ((im.naturalWidth || 0) >= 200 && (im.naturalHeight || 0) >= 200 && area > bestArea) {
          bestArea = area;
          imageUrl = src;
        }
      }
    }
    if (imageUrl) {
      try { imageUrl = new URL(imageUrl, location.href).href; } catch (e) {}
    }
    var title = meta(['og:title']) || ldTitle || (document.title ? document.title.trim() : null);
    var price = meta(['og:price:amount', 'product:price:amount']) || ldPrice;
    return JSON.stringify({ title: title, imageUrl: imageUrl, price: price });
  } catch (e) {
    return null;
  }
})();
""".trimIndent()
