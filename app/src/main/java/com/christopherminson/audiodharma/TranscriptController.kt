package com.christopherminson.audiodharma


import android.app.SearchManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.webkit.WebView


class TranscriptController : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.transcript)


        var webView = findViewById<WebView>(R.id.transcriptView)
        webView.settings.javaScriptEnabled = true

        val url = intent.getStringExtra("URL")

        webView.loadUrl(url)

    }

}