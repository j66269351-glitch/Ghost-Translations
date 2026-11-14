package studio.j.webapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.app.Activity
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var recognizer: SpeechRecognizer? = null
    private var pendingLang = "en-US"
    private var pendingInterim = false
    private var startHost = "appassets.androidplatform.net"
    private var docStart = false
    private var bridgeJs = ""
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileCb?.onReceiveValue(
                if (r.resultCode == Activity.RESULT_OK)
                    WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data) else null)
            fileCb = null
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startRecognition(pendingLang, pendingInterim)
            else { emit("error", "not-allowed"); emit("end", "") }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
        }
        web.addJavascriptInterface(Bridge(), "AndroidSR")
        bridgeJs = assets.open("android-bridge.js").bufferedReader().readText()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, bridgeJs, setOf("*"))
            docStart = true
        }
        val link = try { assets.open("url.txt").bufferedReader().readText().trim() } catch (e: Exception) { "" }
        if (link.isNotEmpty()) startHost = Uri.parse(link).host ?: startHost

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                return try { fileChooser.launch(p.createIntent()); true }
                catch (e: Exception) { fileCb = null; false }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun onPageStarted(view: WebView, url: String?, f: android.graphics.Bitmap?) {
                if (!docStart) view.evaluateJavascript(bridgeJs, null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val h = request.url.host
                if (h == startHost || h == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) {}
                return true
            }
        }
        web.loadUrl(if (link.isNotEmpty()) link
                    else "https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    inner class Bridge {
        @JavascriptInterface
        fun start(lang: String, interim: Boolean) = runOnUiThread {
            pendingLang = lang; pendingInterim = interim
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) startRecognition(lang, interim)
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        @JavascriptInterface
        fun stop() = runOnUiThread { recognizer?.stopListening() }

        @JavascriptInterface
        fun abort() = runOnUiThread {
            recognizer?.cancel(); recognizer?.destroy(); recognizer = null
            emit("end", "")
        }

        @JavascriptInterface
        fun readClip(): String {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: ""
        }

        @JavascriptInterface
        fun writeClip(text: String) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Ghost Translation", text))
        }
    }

    private fun startRecognition(lang: String, interim: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emit("error", "service-not-allowed"); emit("end", ""); return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { emit("start", "") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) emit("result", JSONObject().put("t", t).put("final", false).toString())
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) emit("result", JSONObject().put("t", t).put("final", true).toString())
                emit("end", ""); release(r)
            }
            override fun onError(code: Int) {
                val name = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> "network"
                    SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "aborted"
                    else -> "language-not-supported"
                }
                emit("error", name); emit("end", ""); release(r)
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, interim)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000L)
        })
    }

    // free the mic fully after each session so voice playback is never muffled
    private fun release(r: SpeechRecognizer) = web.post {
        try { r.destroy() } catch (_: Exception) {}
        if (recognizer === r) recognizer = null
    }

    private fun emit(type: String, data: String) {
        val js = "window.__srEvt&&window.__srEvt('$type',${JSONObject.quote(data)})"
        runOnUiThread { web.evaluateJavascript(js, null) }
    }

    override fun onDestroy() {
        recognizer?.destroy(); recognizer = null
        web.destroy()
        super.onDestroy()
    }
}
