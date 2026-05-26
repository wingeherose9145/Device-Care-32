package com.system.helper

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WordActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        setContentView(webView)

        initWebView()

        loadWord()
    }

    /**
     * 初始化 WebView
     */
    private fun initWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = false

        settings.domStorageEnabled = true

        settings.loadsImagesAutomatically = true

        settings.allowFileAccess = true

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.builtInZoomControls = false

        settings.displayZoomControls = false

        settings.useWideViewPort = true

        settings.loadWithOverviewMode = true

        settings.defaultTextEncodingName = "utf-8"

        webView.webViewClient = WebViewClient()
    }

    /**
     * 加载词条
     */
    private fun loadWord() {

        val word = intent.getStringExtra("word") ?: ""

        val html = intent.getStringExtra("html") ?: ""

        title = word

        val finalHtml = wrapHtml(html)

        webView.loadDataWithBaseURL(
            "https://localhost/",
            finalHtml,
            "text/html",
            "utf-8",
            null
        )
    }

    /**
     * 包装 HTML
     */
    private fun wrapHtml(content: String): String {

        return """
            <!DOCTYPE html>
            <html>
            <head>

                <meta charset="utf-8"/>

                <meta
                    name="viewport"
                    content="width=device-width, initial-scale=1.0"
                />

                <style>

                    body{
                        padding:16px;
                        font-size:18px;
                        line-height:1.6;
                        background:#ffffff;
                        color:#111111;
                        word-break:break-word;
                    }

                    img{
                        max-width:100%;
                    }

                    a{
                        color:#2962ff;
                        text-decoration:none;
                    }

                    table{
                        max-width:100%;
                    }

                </style>

            </head>

            <body>

                $content

            </body>

            </html>
        """.trimIndent()
    }

    override fun onBackPressed() {

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
