package com.system.helper

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class WordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        val html = intent.getStringExtra("html") ?: ""
        val word = intent.getStringExtra("word") ?: ""

        title = word

        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = false

        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            wrapHtml(html),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun wrapHtml(html: String): String {
        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                $html
            </body>
            </html>
        """.trimIndent()
    }
}
