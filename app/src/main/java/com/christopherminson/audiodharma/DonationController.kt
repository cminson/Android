package com.christopherminson.audiodharma


import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView


class DonationController : AppCompatActivity() {

    val ButtonDonate: Button by lazy { findViewById<Button>(R.id.buttonDonate) }
    val TextDonate: TextView by lazy { findViewById<TextView>(R.id.donateView) }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.donate)

        ButtonDonate.setOnClickListener { displayWebPage(URL_DONATE) }
    }


    fun displayWebPage(url: String) {

        if (TheDataModel.isInternetAvailable() == false) {

            val dialog = AlertDialog.Builder(this).create()
            dialog.setTitle("Can Not Connect To Audio Dharma")
            dialog.setMessage("Please check your internet connection or try again in a few minutes.")

            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })

            dialog.show()

            return
        }

        val intent = Intent(this, TranscriptController::class.java)
        intent.putExtra("URL", url)
        startActivity(intent)
    }

}