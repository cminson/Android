package com.christopherminson.audiodharma

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import android.media.AudioManager
import android.view.ViewGroup
import android.os.Handler
import java.io.FileInputStream
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import com.christopherminson.audiodharma.R.id.StatusHeader
import android.graphics.PorterDuff



enum class TALK_PLAY_STATE { INIT, LOADING, PLAYING, PAUSED, STOPPED } // all mp3 states

var TalkPlayState : TALK_PLAY_STATE = TALK_PLAY_STATE.INIT

val FAST_SEEK  = 30 * 1000  // number of msecs to move for each Seek operation

var ResumeTalkMode = false      // are we currently playing a talk that is a resumption of a previous talk
var PlayingTalk : TalkData = TalkData()     // talk we are playing
var PlayTalkList : ArrayList<TalkData> = arrayListOf()  // the list the talk is in
var PlayTalkIndex: Int = 0      // the index of the talk
var SecondsIntoTalk: Int = 0    // how far into the talk we are


class MP3Controller : AppCompatActivity() {

    var TheMediaPlayer: MediaPlayer? = null

    lateinit var StatusHeader: TextView
    lateinit var TalkTitle: TextView
    lateinit var TalkSpeaker: TextView

    lateinit var ButtonPlayPause: Button
    lateinit var BusyIndicator: ProgressBar
    lateinit var ButtonFastForward: Button
    lateinit var ButtonFastBackward: Button

    lateinit var ButtonSequenceOnOff: Button
    lateinit var TextSequence: TextView

    lateinit var LinkTranscript: TextView
    lateinit var LinkDonate: TextView

    lateinit var TimeCurrent: TextView
    lateinit var TimeMax: TextView
    lateinit var TalkBar: SeekBar
    /*
    val StatusHeader: TextView by lazy { findViewById<TextView>(R.id.StatusHeader) }
    val TalkTitle: TextView by lazy { findViewById<TextView>(R.id.talkTitle) }
    val TalkSpeaker: TextView by lazy { findViewById<TextView>(R.id.talkSpeaker) }

    val ButtonPlayPause: Button by lazy { findViewById<Button>(R.id.buttonPlayPause) }
    val BusyIndicator: ProgressBar by lazy { findViewById<ProgressBar>(R.id.busyIndicator) }
    val ButtonFastForward: Button by lazy { findViewById<Button>(R.id.buttonFastForward) }
    val ButtonFastBackward: Button by lazy { findViewById<Button>(R.id.buttonFastBackward) }
    val ButtonSequenceOnOff: Button by lazy { findViewById<Button>(R.id.buttonSequenceOnOff) }
    val TextSequence: TextView by lazy { findViewById<TextView>(R.id.textSequence) }

    val LinkTranscript: TextView by lazy { findViewById<TextView>(R.id.linkTranscript) }
    val LinkDonate: TextView by lazy { findViewById<TextView>(R.id.linkDonate) }

    val TimeCurrent: TextView by lazy { findViewById<TextView>(R.id.timeCurrent) }
    val TimeMax: TextView by lazy { findViewById<TextView>(R.id.timeMax) }
    val TalkBar: SeekBar by lazy { findViewById<SeekBar>(R.id.talkBar) }
    */

    var SequenceModeOn = false
    var WebViewActive = false

    val PDF_PREFIX_VIEW = "https://docs.google.com/viewer?url="     // prefix necessary to show pdfs in webview


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        println("AD: MP3 Create")

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setContentView(R.layout.player)
        this.setFinishOnTouchOutside(false)

        StatusHeader = findViewById<TextView>(R.id.StatusHeader)
        TalkTitle = findViewById<TextView>(R.id.talkTitle)
        TalkSpeaker = findViewById<TextView>(R.id.talkSpeaker)

        ButtonPlayPause = findViewById<Button>(R.id.buttonPlayPause)
        BusyIndicator = findViewById<ProgressBar>(R.id.busyIndicator)
        ButtonFastForward = findViewById<Button>(R.id.buttonFastForward)
        ButtonFastBackward = findViewById<Button>(R.id.buttonFastBackward)
        ButtonSequenceOnOff = findViewById<Button>(R.id.buttonSequenceOnOff)
        TextSequence = findViewById<TextView>(R.id.textSequence)

        LinkTranscript = findViewById<TextView>(R.id.linkTranscript)
        LinkDonate = findViewById<TextView>(R.id.linkDonate)

        TimeCurrent = findViewById<TextView>(R.id.timeCurrent)
        TimeMax = findViewById<TextView>(R.id.timeMax)
        TalkBar = findViewById<SeekBar>(R.id.talkBar)


        ButtonPlayPause.setOnClickListener { this.toggleStartStopTalk() }
        ButtonFastForward.setOnClickListener { this.fastForward() }
        ButtonFastBackward.setOnClickListener { this.fastBackward() }
        ButtonSequenceOnOff.setOnClickListener { this.toggleSequenceOnOff() }

        ButtonFastForward.isEnabled = false
        ButtonFastBackward.isEnabled = false

        BusyIndicator.visibility = View.INVISIBLE
        BusyIndicator.getIndeterminateDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.MULTIPLY);

        TalkBar.max = 100

        TalkBar.getProgressDrawable().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        //                                            TalkBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.MULTIPLY);

        TalkBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekToPercentage(seekBar?.progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekToPercentage(seekBar?.progress)
            }
        })

        LinkTranscript.setOnClickListener { displayWebPage(PlayingTalk.PDF) }
        LinkDonate.setOnClickListener { displayDonationIntroduction() }

        if (PlayTalkList.count() == 0) {

            alertTalkUnavailable()
            return
        }

        try {
            PlayingTalk = PlayTalkList[PlayTalkIndex]

        } catch (e: Exception) {    // this shouldn't happen ...

            alertTalkUnavailable()
            return
        }

        if (ResumeTalkMode == true) {

            getSupportActionBar()?.setTitle("Resuming Talk")
            resetDisplay()

            ButtonSequenceOnOff.isEnabled = false
            ButtonSequenceOnOff.visibility = View.INVISIBLE
            TextSequence.visibility = View.INVISIBLE

            updateProgressBar(SecondsIntoTalk)

            val currentSeconds = TheDataModel.secondsToDurationDisplay(SecondsIntoTalk)
            StatusHeader.text = "Resuming Talk $currentSeconds"
            TimeCurrent.text = currentSeconds
            updateDisplayHeader()

        } else {

            getSupportActionBar()?.setTitle("Play Talk")
            resetDisplay()
        }
    }


    override fun onPause() {
        super.onPause()

        //if (WebViewActive == false) stopTalk()
    }

    override fun onResume() {
        super.onResume()

        //println("AD onResume")

        WebViewActive = false

    }

    override fun onStop() {
        super.onStop()

        //println("AD onStop")

        //if (WebViewActive == false) stopTalk()
    }

    override fun onDestroy() {
        super.onDestroy()

        //println("AD onDestroy")

        WebViewActive = false
        stopTalk()
    }


    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////

    fun alertTalkUnavailable() {

        val dialog = AlertDialog.Builder(this).create()
        dialog.setTitle("This Talk Is Unavailable")
        dialog.setMessage("Please check your internet connection or try again later.")
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
        dialog.show()
    }

    fun toggleStartStopTalk() {

        when (TalkPlayState) {
            TALK_PLAY_STATE.INIT -> {
                startNewTalk()
            }
            TALK_PLAY_STATE.LOADING -> {
            }
            TALK_PLAY_STATE.STOPPED -> {
                startNewTalk()
            }
            TALK_PLAY_STATE.PLAYING -> {
                pauseTalk()
            }
            TALK_PLAY_STATE.PAUSED -> {
                unpauseTalk()
            }
        }
    }


    fun startNewTalk() {

        var url: String

        // if this is not a downloaded talk and the network is not available, then alert user that no talk can play
        if ((TheDataModel.isDownloadTalk(PlayingTalk) == false) and (TheDataModel.isInternetAvailable() == false)) {

            val dialog = AlertDialog.Builder(this).create()
            dialog.setTitle("Can Not Connect To Audio Dharma")
            dialog.setMessage("Please check your internet connection or try again in a few minutes.")
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
            dialog.show()
            return
        }

        TalkPlayState = TALK_PLAY_STATE.LOADING

        BusyIndicator.visibility = View.VISIBLE

        updateDisplayHeader()

        TheMediaPlayer = MediaPlayer()

        val imageResource = resources.getIdentifier("@drawable/ic_playpause", null, this.getPackageName())
        ButtonPlayPause.background = ContextCompat.getDrawable(this, imageResource)

        TalkPlayState = TALK_PLAY_STATE.PLAYING

        println("AD: Starting Talk")
        TheMediaPlayer?.setOnPreparedListener(object : MediaPlayer.OnPreparedListener {

            override fun onPrepared(p0: MediaPlayer?) {

                TheMediaPlayer?.start()
                if (ResumeTalkMode) TheMediaPlayer?.seekTo(SecondsIntoTalk * 1000)
                startTalkTimer()

                TheMediaPlayer?.setOnCompletionListener { talkCompletes() }

                updateDisplayHeader()
                ButtonFastForward.isEnabled = true
                ButtonFastBackward.isEnabled = true

                BusyIndicator.visibility = View.INVISIBLE
            }
        })


        if (TheDataModel.isDownloadTalk(PlayingTalk)) {

            url = MP3_DOWNLOADS_PATH + "/" + PlayingTalk.FileName

            try {
                val fileStream = FileInputStream(url)
                TheMediaPlayer?.setDataSource(fileStream.fd)

            } catch (e: Exception) {

                stopTalk()
                BusyIndicator.visibility = View.INVISIBLE

                val dialog = AlertDialog.Builder(this).create()
                dialog.setTitle("Could Not Play Talk")
                dialog.setMessage("The talk is not fully downloaded. Try again in a few minutes.")
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
                dialog.show()
                return
            }

        } else {

            println("AD: Starting Talk 2")

            url = URL_MP3_HOST + PlayingTalk.URL
            TheMediaPlayer?.setDataSource(url)
        }

        TheMediaPlayer?.prepareAsync()

        updateDisplayHeader()
    }


    fun pauseTalk() {

        TheMediaPlayer?.pause()

        TalkPlayState = TALK_PLAY_STATE.PAUSED

        val imageResource = resources.getIdentifier("@drawable/ic_playactive", null, this.getPackageName())
        ButtonPlayPause.background = ContextCompat.getDrawable(this, imageResource)

        ButtonFastForward.isEnabled = false
        ButtonFastBackward.isEnabled = false

        updateDisplayHeader()
    }


    fun unpauseTalk() {

        TheMediaPlayer?.start()

        TalkPlayState = TALK_PLAY_STATE.PLAYING

        val imageResource = resources.getIdentifier("@drawable/ic_playpause", null, this.getPackageName())
        ButtonPlayPause.background = ContextCompat.getDrawable(this, imageResource)

        ButtonFastForward.isEnabled = true
        ButtonFastBackward.isEnabled = true

        updateDisplayHeader()
    }


    fun stopTalk() {

        updateDisplayHeader()

        TheMediaPlayer?.stop()
        TheMediaPlayer?.release();
        TheMediaPlayer = null

        TalkPlayState = TALK_PLAY_STATE.STOPPED

        val imageResource = resources.getIdentifier("@drawable/ic_playactive", null, this.getPackageName())
        ButtonPlayPause.background = ContextCompat.getDrawable(this, imageResource)

        ButtonFastForward.isEnabled = false
        ButtonFastBackward.isEnabled = false
    }


    fun startTalkTimer() {

        val TimeUpdater = Handler()
        val delay: Long = 1000 //milliseconds

        TimeUpdater.postDelayed(object : Runnable {
            override fun run() {

                if (TheMediaPlayer != null) {

                    if (TheMediaPlayer!!.isPlaying == true) {

                        val secondsIntoTalk = TheMediaPlayer!!.currentPosition / 1000

                        updateDisplayHeader()
                        updateProgressBar(secondsIntoTalk)

                        if ((secondsIntoTalk > REPORT_TALK_THRESHOLD) and (TheDataModel.isMostRecentTalk(PlayingTalk) == false)) {

                            TheDataModel.addToTalkHistory(PlayingTalk)
                            TheDataModel.reportTalkActivity(ACTIVITIES.PLAY_TALK, PlayingTalk)
                        }
                    }
                    TimeUpdater.postDelayed(this, delay)
                }
            }
        }, delay)
    }


    fun talkCompletes() {

        TalkPlayState = TALK_PLAY_STATE.INIT
        stopTalk()
        StatusHeader.text = "Talk Completes"

        ButtonFastForward.isEnabled = false
        ButtonFastBackward.isEnabled = false

        if (SequenceModeOn == true) {

            nextTalk()
            resetDisplay()
            startNewTalk()
        }
    }


    fun nextTalk() {

        PlayTalkIndex += 1
        if (PlayTalkIndex >= PlayTalkList.count()) PlayTalkIndex = 0

        PlayingTalk = PlayTalkList[PlayTalkIndex]
    }


    fun fastForward() {

        if (TheMediaPlayer == null) return

        var newPosition = TheMediaPlayer!!.currentPosition + FAST_SEEK
        if (newPosition < PlayingTalk.DurationInSeconds * 1000) {

            TheMediaPlayer?.seekTo(newPosition)
        }
    }


    fun fastBackward() {

        if (TheMediaPlayer == null) return

        var newPosition = TheMediaPlayer!!.currentPosition - FAST_SEEK
        if (newPosition < 0) newPosition = 0

        TheMediaPlayer?.seekTo(newPosition)
    }


    fun resetDisplay() {

        TalkTitle.text = PlayingTalk.Title

        if (TheDataModel.isDownloadTalk(PlayingTalk)) {
            TalkTitle.setTextColor(BUTTON_DOWNLOAD_COLOR)
        }
        TalkSpeaker.text = PlayingTalk.Speaker

        if (TheDataModel.doesTalkHaveTranscript(PlayingTalk)) {
            LinkTranscript.visibility = View.VISIBLE
        } else {
            LinkTranscript.visibility = View.INVISIBLE
        }

        TimeMax.text = TheDataModel.secondsToDurationDisplay(PlayingTalk.DurationInSeconds)
        TalkBar.setProgress(0)
        updateDisplayHeader()
    }


    fun seekToPercentage(percentageComplete: Int?) {

        if (TheMediaPlayer == null) return
        if (percentageComplete == null) return

        val durationInSeconds = PlayingTalk.DurationInSeconds.toFloat()
        val progressRatio: Float = 100 / durationInSeconds

        var newPosition: Int = (percentageComplete / progressRatio).toInt()

        if (newPosition < 0) {
            newPosition = 0
        }
        TheMediaPlayer!!.seekTo(newPosition * 1000)
    }


    fun updateProgressBar(secondsIntoTalk: Int) {

        val durationInSeconds = PlayingTalk.DurationInSeconds.toFloat()
        val progressRatio: Float = 100 / durationInSeconds
        val progressValue: Int = (secondsIntoTalk * progressRatio).toInt()

        TalkBar.setProgress(progressValue)

        // store the current state of this playing talk
        // used by the goto Talk button in AlbumController
        val prefs = PreferenceManager.getDefaultSharedPreferences(this).edit()
        prefs?.putString(KEY_PLAYINGTALK_NAME, PlayingTalk.FileName)
        prefs?.putInt(KEY_PLAYINGTALK_POSITION, secondsIntoTalk)
        prefs?.apply()
    }


    fun updateDisplayHeader() {

        var state = ""

        when (TalkPlayState) {
            TALK_PLAY_STATE.INIT -> {
                state = ""
            }
            TALK_PLAY_STATE.LOADING -> {
                state = "Loading"
            }
            TALK_PLAY_STATE.STOPPED -> {
                state = "Stopped"
            }
            TALK_PLAY_STATE.PLAYING -> {
                state = "Playing"
            }
            TALK_PLAY_STATE.PAUSED -> {
                state = "Paused"
            }
        }

        if (TheMediaPlayer == null) return

        var positionInSeconds = TheMediaPlayer!!.currentPosition / 1000
        val durationDisplay = TheDataModel.secondsToDurationDisplay(positionInSeconds)
        val displayIndex = PlayTalkIndex + 1

        if (SequenceModeOn) {

            val count = PlayTalkList.count()
            StatusHeader.text = "$state $displayIndex/$count $durationDisplay"

        } else {
            StatusHeader.text = "$state $durationDisplay"
        }

        TimeCurrent.text = durationDisplay
    }


    fun toggleSequenceOnOff() {

        var imageName: String

        if (SequenceModeOn == true) {

            SequenceModeOn = false
            imageName = "@drawable/ic_sequenceoff"

        } else {

            SequenceModeOn = true
            imageName = "@drawable/ic_sequenceon"
        }

        val imageResource = resources.getIdentifier(imageName, null, this.getPackageName())
        ButtonSequenceOnOff.background = ContextCompat.getDrawable(this, imageResource)

    }


    fun displayDonationDialogText() {

        var textIntro = "Audio Dharma is a free service provided by the Insight Meditation Center in Redwood City, California. IMC is run solely by volunteers and does not require payment for any of its programs. Our financial support comes from the generosity of people who value what we do."
        var textDonate = "If you wish to make a donation, please go to audiodharma.org and click the Donation link."

        val dialog = AlertDialog.Builder(this).create()

        dialog.setTitle("Dana")
        dialog.setMessage(textIntro + "\n\n" + textDonate)

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
        dialog.show()
    }


    fun displayDonationDialog() {

        val dialog = AlertDialog.Builder(this).create()
        dialog.setTitle("Return To Talk Player");

        var url = "https://audiodharma.org/donate/"
        val webView = WebView(this)
        webView.loadUrl(url)
        dialog.setView(webView)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
        dialog.show()
    }

    fun displayDonationIntroduction () {

        WebViewActive = true

        val intent = Intent(this, DonationController::class.java)
        startActivity(intent)
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

        var webURL = url
        if (url.contains("pdf", true))
            webURL = PDF_PREFIX_VIEW + url

        val intent = Intent(this, TranscriptController::class.java)
        intent.putExtra("URL", webURL)
        startActivity(intent)
        WebViewActive = true

    }


}



