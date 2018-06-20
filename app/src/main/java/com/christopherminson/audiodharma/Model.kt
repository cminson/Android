package com.christopherminson.audiodharma

import android.graphics.Color
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.*
import android.preference.PreferenceManager
import java.net.URL
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import com.christopherminson.audiodharma.R.id.*
import com.github.kittinunf.fuel.httpPut
import java.io.*
import kotlin.concurrent.fixedRateTimer
import android.widget.TextView
import kotlin.text.Typography.section
import android.net.NetworkInfo
import android.net.ConnectivityManager
import android.support.v7.app.AppCompatActivity
import com.github.kittinunf.fuel.httpPost
import org.jetbrains.anko.doAsync
import org.json.JSONArray
import java.net.HttpURLConnection


val TheDataModel = Model()
var TheUserLocation = UserLocation()
//var DEVICE_ID = UUID.randomUUID().toString()
var DEVICE_ID : String? = "NA"


// all possible web config points
val HostAccessPoints = arrayOf(
"http://www.virtualdharma.org",
"http://www.ezimba.com",
"http://www.audiodharma.org"
)
val HostAccessPoint: String = HostAccessPoints[0]   // the one we're currently using

// paths for services
val CONFIG_ZIP_NAME = "CONFIG00.ZIP"
val CONFIG_JSON_NAME = "CONFIG00.JSON"

//val CONFIG_ZIP_NAME = "DEV00.ZIP"
//val CONFIG_JSON_NAME = "DEV00.JSON"

var APP_ROOT_PATH = ""      // root for all app storage
var MP3_DOWNLOADS_PATH = ""      // where MP3s are downloaded.  this is set up in loadData()

var CONFIG_ACCESS_PATH = "/AudioDharmaAppBackend/Config/" + CONFIG_ZIP_NAME    // remote web path to config
var CONFIG_REPORT_ACTIVITY_PATH = "/AudioDharmaAppBackend/Access/reportactivity.php"     // where to report user activity (shares, listens)
//var CONFIG_GET_ACTIVITY_PATH = "/AudioDharmaAppBackend/Access/getactivity.php"           // where to get sangha activity (shares, listens)
var CONFIG_GET_ACTIVITY_PATH = "/AudioDharmaAppBackend/Access/XGETACTIVITY.php?"        // where to get sangha activity (shares, listens)
val DEFAULT_MP3_PATH = "https://www.audiodharma.org"     // where to get talks
val DEFAULT_DONATE_PATH = "https://audiodharma.org/donate/"       // where to donate

var HTTPResultCode: Int = 0     // global status of web access
val MIN_EXPECTED_RESPONSE_SIZE = 300   // to filter for bogus redirect page responses

enum class INIT_CODES {          // all possible startup results
    SUCCESS, NO_CONNECTION
}

// set default web access points
var URL_CONFIGURATION = HostAccessPoint + CONFIG_ACCESS_PATH
var URL_REPORT_ACTIVITY = HostAccessPoint + CONFIG_REPORT_ACTIVITY_PATH
var URL_GET_ACTIVITY = HostAccessPoint + CONFIG_GET_ACTIVITY_PATH
var URL_MP3_HOST = DEFAULT_MP3_PATH
var URL_DONATE = DEFAULT_DONATE_PATH

data class TalkData  (
    var Title: String = "",
    var URL: String = "",
    var FileName: String = "",
    var Date: String = "",
    var Speaker: String = "",
    var Section: String = "",
    var DurationDisplay: String = "",
    var PDF: String = "",
    var Keys: String = "",
    var DurationInSeconds: Int = 0,
    var SpeakerPhoto: String = "",
    var CityPlayed: String = "",
    var StatePlayed: String = "",
    var CountryPlayed: String = ""
)

data class AlbumData (
    var Title: String = "",
    var Content: String = "",
    var Section: String = "",
    var Image: String = "",
    var Date: String = ""
)

data class AlbumStats  ( // where stats on each album is kept
    var totalTalks: Int,
    var totalSeconds: Int,
    var durationDisplay: String
)

data class UserAlbumData  (
    var Title: String = "",
    var TalkFileNames: ArrayList<String> = ArrayList<String> (),
    var Content: String = ""
)

data class UserDownloadData  (
    var DownloadCompleted: Boolean = false
)

data class UserFavoriteData  (
        var FileName: String = ""
)

data class  UserLocation (       // where user geo info is kept
    val city: String = "NA",
    val state: String = "NA",
    val country: String = "NA",
    val zip: String = "NA",
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
    )

enum class ACTIVITIES {          // all possible activities that are reported back to cloud
    SHARE_TALK, PLAY_TALK
}

// App Global Constants
// talk and album display states.  these are used throughout the app to key on state
val KEY_ALBUMROOT = "KEY_ALBUMROOT"
val KEY_TALKS = "KEY_TALKS"
val KEY_ALLTALKS = "KEY_ALLTALKS"
val KEY_GIL_FRONSDAL = "Gil Fronsdal"
val KEY_ANDREA_FELLA = "Andrea Fella"
val KEY_ALLSPEAKERS = "KEY_ALLSPEAKERS"
val KEY_ALL_SERIES = "KEY_ALL_SERIES"
val KEY_DHARMETTES = "KEY_DHARMETTES"
val KEY_RECOMMENDED_TALKS = "KEY_RECOMMENDED_TALKS"
val KEY_NOTES = "KEY_NOTES"
val KEY_USER_SHAREHISTORY = "KEY_USER_SHAREHISTORY"
val KEY_USER_TALKHISTORY = "KEY_USER_TALKHISTORY"
val KEY_USER_FAVORITES = "KEY_USER_FAVORITES"
val KEY_USER_DOWNLOADS = "KEY_USER_DOWNLOADS"
val KEY_SANGHA_TALKHISTORY = "KEY_SANGHA_TALKHISTORY"
val KEY_SANGHA_SHAREHISTORY = "KEY_SANGHA_SHAREHISTORY"
val KEY_USER_ALBUMS = "KEY_USER_ALBUMS"
val KEY_USEREDIT_ALBUMS = "KEY_USEREDIT_ALBUMS"
val KEY_USER_TALKS = "KEY_USER_TALKS"
val KEY_USEREDIT_TALKS = "KEY_USEREDIT_TALKS"
val KEY_PLAY_TALK = "KEY_PLAY_TALK"

val BUTTON_NOTE_COLOR = Color.parseColor("#0057CC")     //  blue #0057CC
val BUTTON_FAVORITE_COLOR = Color.parseColor("#ff8c00")     //  orange #ff8c00
val BUTTON_SHARE_COLOR = Color.parseColor("#62b914")     //  green #62b914
val BUTTON_DOWNLOAD_COLOR = Color.parseColor("#CC1F00")     //  red #CC1F00
val APP_ICON_COLOR = Color.parseColor("#62b914")     //  green #62b914

val SECTION_BACKGROUND = Color.parseColor("#0057CC")  // #555555ff
val MAIN_FONT_COLOR = Color.parseColor("#555555")      // #555555ff
val SECONDARY_FONT_COLOR = Color.parseColor("#555555")
val SECTION_TEXT = Color.WHITE

var MP3_BYTES_PER_SECOND = 20000    // rough (high) estimate for how many bytes per second of MP3.  Used to estimate size of download files

// MARK: Global Config Variables.  Values are defaults.  All these can be overriden at boot time by the config
var REPORT_TALK_THRESHOLD = 90      // how many seconds into a talk before reporting that talk that has been officially played
var SECONDS_TO_NEXT_TALK : Double = 2.0   // when playing an album, this is the interval between talks

var MAX_TALKHISTORY_COUNT = 2000     // maximum number of played talks showed in sangha history
var MAX_SHAREHISTORY_COUNT = 2000     // maximum number of shared talks showed in sangha history
var MAX_USERTALKHISTORY_COUNT = 1000     // maximum number of played talks showed in user  historyfffv
var MAX_USERSHAREHISTORY_COUNT = 1000     // maximum number of shared talks showed user  history

var USE_NATIVE_MP3PATHS = true    // true = mp3s are in their native paths in audiodharma, false =  mp3s are in one flat directory

val STORAGE_DELIMITER = "$!$"       // string that segments fields for persistant storage
val STORAGE_MIN = 10                // minimum size of storage, used to filter out any cruff

val KEY_PLAYINGTALK_NAME = "PLAYINGTALK_NAME" // where the current talk name (that is playing) is stored
val KEY_PLAYINGTALK_POSITION = "PLAYINGTALK_POSITION" // the position in seconds of that talk

fun String.LOG() =  println("AD: " + this)

fun JSONObject.getArg(key: String) : String?  {

    var arg: String? = null
    if (this.has(key)) {
        arg = this.getString(key)
    }
    return arg
}
fun JSONObject.getArgInt(key: String) : Int?  {

    var arg: Int? = null
    if (this.has(key)) {
        arg = this.getInt(key)
    }
    return arg
}
fun JSONObject.getArgBoolean(key: String) : Boolean?  {

    var arg: Boolean? = null
    if (this.has(key)) {
        arg = this.getBoolean(key)
    }
    return arg
}

val UPDATE_MODEL_INTERVAL : Long = 120 * 60 //  seconds before full model reload.
var UPDATE_SANGHA_INTERVAL = 60     // interval seconds for  updated sangha info.

var LAST_MODEL_UPDATE = System.currentTimeMillis() / 1000

var ConnectivityService: ConnectivityManager? = null

val SECTION_HEADER = "SECTION_HEADER"
val DATA_ALBUMS: ArrayList<String> = arrayListOf("DATA00", "DATA01", "DATA02", "DATA03", "DATA04", "DATA05")   // all possible pluggable data albums we can load

var AlbumRefreshHandler : Handler? = null       // handler for the root album.  the background sends UI refresh messages thru this object
var TalkRefreshHandler : Handler? = null        // handler for the talk album.  the background sends UI refresh messages thru this object



class Model {

    var RootAlbum = ArrayList<AlbumData>() // The Main Album
    var SpeakerAlbums = ArrayList<AlbumData>() // array of Albums for all speakers
    var SeriesAlbums = ArrayList<AlbumData>() // array of Albums for all series
    var RecommendedAlbums = ArrayList<AlbumData>() // array of recommended Albums

    var KeyToTalks = HashMap<String, ArrayList<TalkData>>() // dictionary keyed by content, value talk list
    var KeyToAlbumStats = HashMap<String, AlbumStats>()
    var FileNameToTalk = HashMap<String, TalkData>()

    var UserTalkHistoryAlbum = ArrayList<TalkData>()
    var UserShareHistoryAlbum = ArrayList<TalkData>()

    var UserTalkHistoryStats: AlbumStats? = null

    var SanghaTalkHistoryAlbum = ArrayList<TalkData>()
    var SanghaTalkHistoryStats: AlbumStats? = null

    var SanghaShareHistoryAlbum = ArrayList<TalkData>()
    var SanghaShareHistoryStats: AlbumStats? = null

    var AllTalks = ArrayList<TalkData>()

    var UserAlbums = HashMap<String, UserAlbumData>()
    var UserNotes = HashMap<String, String>()
    var UserFavorites = HashMap<String, Boolean>()
    var UserDownloads = HashMap<String, Boolean>()

    var Initialized = false
    var StorageHandle: SharedPreferences? = null
    var UpdateSanghaHistoryTimer: Timer? = null  // the timer for sangha activity refresh


    fun loadData(context: Context) {

        StorageHandle = PreferenceManager.getDefaultSharedPreferences(context)
        setDeviceID()

        HTTPResultCode = 0
        URL_CONFIGURATION = HostAccessPoint + CONFIG_ACCESS_PATH
        URL_REPORT_ACTIVITY = HostAccessPoint + CONFIG_REPORT_ACTIVITY_PATH
        URL_GET_ACTIVITY = HostAccessPoint + CONFIG_GET_ACTIVITY_PATH

        UpdateSanghaHistoryTimer?.cancel()
        UpdateSanghaHistoryTimer = startSanghaHistoryTimer()

        val rootPath = context.getExternalFilesDir(null)?.absolutePath
        APP_ROOT_PATH = rootPath!!
        MP3_DOWNLOADS_PATH = APP_ROOT_PATH + "/DOWNLOADS"
        if (!File(MP3_DOWNLOADS_PATH).mkdirs()) {
        }


        // IMPORTANT: this must be done at init time, otherwise possible race condition
        // in the case where no wifi and stats calculation then reference null content
        for (dataContent in DATA_ALBUMS) {
            KeyToTalks[dataContent] = arrayListOf()
        }

        downloadAndConfigure(URL_CONFIGURATION)
    }

    fun resetData() {

        RootAlbum = arrayListOf()
        SpeakerAlbums = arrayListOf()
        SeriesAlbums = arrayListOf()
        RecommendedAlbums = arrayListOf()

        KeyToTalks = hashMapOf()

        KeyToAlbumStats = hashMapOf()
        FileNameToTalk = hashMapOf()
        UserTalkHistoryAlbum = arrayListOf()
        UserShareHistoryAlbum = arrayListOf()

        SanghaTalkHistoryAlbum = arrayListOf()
        SanghaShareHistoryAlbum = arrayListOf()

        AllTalks = ArrayList<TalkData>()
        AllTalks = arrayListOf()

        UserAlbums = hashMapOf()
        UserNotes = hashMapOf()
        UserFavorites = hashMapOf()
        UserDownloads = hashMapOf()

        for (dataContent in DATA_ALBUMS) {
            KeyToTalks[dataContent] = arrayListOf()
        }

    }


    fun downloadAndConfigure(path: String) {

        val pathZipFile = APP_ROOT_PATH + "/" + CONFIG_ZIP_NAME
        val pathJSONFile = APP_ROOT_PATH + "/" + CONFIG_JSON_NAME

        // get zip file and store it off
        var responseData: ByteArray? = null

        //val test = "http://www.virtualdharma.org/AudioDharmaAppBackend/Config/TEST"
        try {
            responseData = URL(path).readBytes()
            //responseData = URL(test).readBytes()


            var inputStream: InputStream = responseData.inputStream()
            var outFile = File(pathZipFile)
            var outputStream = FileOutputStream(outFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) {
            //"EXCEPTION".LOG()
            e.printStackTrace()
            return
        }

        // unzip it
        if (responseData != null) {
            if (responseData.count() > MIN_EXPECTED_RESPONSE_SIZE) MyUnZip.unzip(pathZipFile, APP_ROOT_PATH)
        }

        // read it into a json object
        var configJSON: String
        try {
            configJSON = File(pathJSONFile).readText()


        } catch (e: Exception) {
            return
        }

        val jsonObj = JSONObject(configJSON)


        loadConfig(jsonObj)
        loadTalks(jsonObj)
        loadAlbums(jsonObj)

        downloadSanghaActivity()

        computeRootAlbumStats()
        computeSpeakerStats()
        computeSeriesStats()
        computeRecommendedStats()

        computeUserAlbumStats()
        computeNotesStats()
        computeUserFavoritesStats()
        computeUserDownloadStats()
        computeTalkHistoryStats()
        computeShareHistoryStats()
        computeDataStats()

        //////////////////
        // DEV
        /*
        val editStorage = StorageHandle?.edit()
        editStorage?.clear()
        editStorage?.commit()
        */

        //////////////////


        UserAlbums = TheDataModel.loadUserAlbumData()
        computeUserAlbumStats()
        UserNotes = TheDataModel.loadUserNoteData()
        computeNotesStats()
        UserFavorites = TheDataModel.loadUserFavoriteData()
        computeUserFavoritesStats()
        UserDownloads = TheDataModel.loadUserDownloadData()

        TheDataModel.validateUserDownloadData()
        this.computeUserDownloadStats()

        this.UserTalkHistoryAlbum = TheDataModel.loadTalkHistoryData()
        this.computeTalkHistoryStats()
        this.UserShareHistoryAlbum = TheDataModel.loadShareHistoryData()
        this.computeShareHistoryStats()

    }


    fun loadConfig(jsonObj: JSONObject) {

        val config = jsonObj.getJSONObject("config")

        URL_MP3_HOST = config.getArg("URL_MP3_HOST") ?: URL_MP3_HOST
        USE_NATIVE_MP3PATHS = config.getArgBoolean("USE_NATIVE_MP3PATHS") ?: true

        URL_REPORT_ACTIVITY = config.getArg("URL_REPORT_ACTIVITY") ?: URL_REPORT_ACTIVITY
        URL_GET_ACTIVITY = config.getArg("URL_GET_ACTIVITY") ?: URL_GET_ACTIVITY

        URL_DONATE = config.getArg("URL_DONATE") ?: URL_DONATE

        MAX_TALKHISTORY_COUNT = config.getArgInt("MAX_TALKHISTORY_COUNT") ?: MAX_TALKHISTORY_COUNT
        MAX_SHAREHISTORY_COUNT = config.getArgInt("MAX_SHAREHISTORY_COUNT") ?: MAX_SHAREHISTORY_COUNT

        UPDATE_SANGHA_INTERVAL = config.getArgInt("UPDATE_SANGHA_INTERVAL") ?: UPDATE_SANGHA_INTERVAL

    }


    fun loadTalks(jsonObj: JSONObject) {

        var talkCount = 0
        var totalSeconds = 0

        val talks = jsonObj.getJSONArray("talks")

        for (i in 0..(talks.length() - 1)) {
            val talk = talks.getJSONObject(i)

            var series = talk.getArg("series") ?: ""
            var title = talk.getArg("title") ?: ""
            var section = talk.getArg("section") ?: ""
            var url = talk.getArg("url") ?: ""
            var speaker = talk.getArg("speaker") ?: ""
            var date = talk.getArg("date") ?: ""
            var duration = talk.getArg("duration") ?: ""
            var pdf = talk.getArg("pdf") ?: ""
            var keys = talk.getArg("keys") ?: ""

            var fileName = getFileNameFromPath(url)

            var seconds = this.convertDurationToSeconds(duration)
            totalSeconds += seconds

            var talkData = TalkData(
                    Title = title,
                    URL = url,
                    FileName = fileName,
                    Date = date,
                    Speaker = speaker,
                    Section = section,
                    DurationDisplay = duration,
                    DurationInSeconds = seconds,
                    PDF = pdf,
                    Keys = keys,
                    SpeakerPhoto = speaker)


            if (doesTalkHaveTranscript(talkData)) {
                talkData.Title = talkData.Title + " [transcript]"
            }

            this.FileNameToTalk[fileName] = talkData

            // add this talk to  list of all talks
            this.AllTalks.add(talkData)

            if (this.KeyToTalks[speaker] == null) {

                this.KeyToTalks[speaker] = ArrayList<TalkData>()
                this.KeyToTalks[speaker]?.add(talkData)


                // create a Album for this speaker and add to array of speaker Albums
                // this array will be referenced by SpeakersController
                var albumData = AlbumData(speaker, speaker, "", speaker, date)
                this.SpeakerAlbums.add(albumData)
            } else {

                this.KeyToTalks[speaker]?.add(talkData)
            }

            // if a series is specified, add to a series list
            if (series.length > 1) {

                val seriesKey = "SERIES" + series

                if (this.KeyToTalks[seriesKey] == null) {

                    this.KeyToTalks[seriesKey] = ArrayList<TalkData>()
                    this.KeyToTalks[seriesKey]?.add(talkData)

                    // create a Album for this series and add to array of series Albums
                    // this array will be referenced by SeriesController
                    var albumData = AlbumData(series, seriesKey, "", speaker, date)
                    this.SeriesAlbums.add(albumData)

                } else {
                    this.KeyToTalks[seriesKey]?.add(talkData)
                }
            }

            talkCount += 1
        }

        var durationDisplay = this.secondsToDurationDisplay(totalSeconds)

        val stats = AlbumStats(talkCount, totalSeconds, durationDisplay)
        this.KeyToAlbumStats[KEY_ALLTALKS] = stats

        // sort the albums and ALL_TALKS
        this.SpeakerAlbums = ArrayList(this.SpeakerAlbums.sortedWith(compareBy({ it.Content })))
        this.SeriesAlbums = ArrayList(this.SeriesAlbums.sortedWith(compareBy({ it.Date })))
        Collections.reverse(SeriesAlbums)
        this.AllTalks = ArrayList(this.AllTalks.sortedWith(compareBy({ it.Date })))
        Collections.reverse(AllTalks)

        // also sort all talks in series albums (note: must take possible sections into account)
        for (seriesAlbum in this.SeriesAlbums) {

            // dharmettes are already sorted and need to be presented with most current talks on top
            // all other series need further sorting, as the most current talks must be at bottom
            if (seriesAlbum.Content == "SERIESDharmettes") {
                continue
            }

            var talkList = KeyToTalks[seriesAlbum.Content]
            if (talkList != null) {

                val sortedTalkList = talkList.sortedWith(compareBy({ it.Date }))
                KeyToTalks[seriesAlbum.Content] = ArrayList(sortedTalkList)
            }
        }
    }


    fun loadAlbums(jsonObj: JSONObject) {

        val albums = jsonObj.getJSONArray("albums")

        var prevAlbumSection = ""
        for (i in 0..albums.length() - 1) {
            val album = albums.getJSONObject(i)

            var albumSection = album.getArg("section") ?: ""
            var albumTitle = album.getArg("title") ?: ""
            var albumContent = album.getArg("content") ?: ""
            var albumImage = album.getArg("image") ?: ""

            // NOTE TBD: for now, we won't support Custom Albums.  Not convinced they're necessary
            if (albumTitle == "Custom Albums") continue

            var albumData = AlbumData(albumTitle, albumContent, albumSection, albumImage, "")

            if ((albumSection != "") and (albumSection != prevAlbumSection)) {
                val albumSectionHeader = AlbumData(Title = SECTION_HEADER, Section = albumSection)
                this.RootAlbum.add(albumSectionHeader)
                prevAlbumSection = albumSection
            }

            this.RootAlbum.add(albumData)

            // get the optional talk array for this Album
            // if exists, store off all the talks in keyToTalks keyed by 'content' id
            var currentSeries = "_"
            if (album.has("talks")) {

                val talkList = album.getJSONArray("talks")

                val count = talkList.length()

                var prevTalkSection = ""
                for (j in 0..(count - 1)) {

                    val talk = talkList.getJSONObject(j)

                    var title = talk.getArg("title") ?: ""
                    var url = talk.getArg("url") ?: ""
                    var section = talk.getArg("section") ?: ""
                    var series = talk.getArg("series") ?: ""

                    var fileName = getFileNameFromPath(url)

                    // DEV NOTE: remove placeholder.  this code might not be necessary long-term
                    if (section == "_" || section == "__") {
                        section = ""
                    }

                    // fill in these fields from talk data.  must do this as these fields are not stored in config json (to make things
                    // easier for config reading)
                    var talkData = this.FileNameToTalk[fileName]
                    if (talkData == null) continue

                    var date = talkData.Date
                    var speaker = talkData.Speaker
                    var durationDisplay = talkData.DurationDisplay
                    var pdf = talkData.PDF
                    var keys = talkData.Keys

                    val totalSeconds = this.convertDurationToSeconds(talkData.DurationDisplay)
                    talkData = TalkData(title,
                            url,
                            fileName,
                            date,
                            speaker,
                            section,
                            durationDisplay,
                            pdf,
                            keys,
                            totalSeconds,
                            "")


                    if (doesTalkHaveTranscript(talkData)) {
                        talkData.Title = talkData.Title + " [transcript]"
                    }

                    // if a series is specified create a series album if not already there.  then add talk to it
                    // otherwise, just add the talk directly to the parent album
                    if (series.length > 1) {

                        if (series != currentSeries) {
                            currentSeries = series
                        }
                        val seriesKey = "RECOMMENDED" + series

                        // create the album if not there already
                        if (this.KeyToTalks[seriesKey] == null) {

                            this.KeyToTalks[seriesKey] = ArrayList<TalkData>()
                            albumData = AlbumData(series, seriesKey, "", speaker, date)
                            this.RecommendedAlbums.add(albumData)
                            this.SeriesAlbums.add(albumData)
                        }

                        if ((section != "") and (section != prevTalkSection)) {
                            val talkSection = TalkData(Title = SECTION_HEADER, Section = section)
                            this.KeyToTalks[seriesKey]?.add(talkSection)
                            prevTalkSection = section
                        }

                        // now add talk to this series album
                        this.KeyToTalks[seriesKey]?.add(talkData)

                    } else {

                        if (this.KeyToTalks[albumContent] == null) this.KeyToTalks[albumContent] = ArrayList<TalkData>()

                        this.KeyToTalks[albumContent]?.add(talkData)
                    }
                }
            }
        } // end Album loop

    }


    fun downloadSanghaActivity() {

        if (TheDataModel.isInternetAvailable() == false) return

        var responseData: String?
        var getActivity = URL_GET_ACTIVITY + "DEVICEID=" + DEVICE_ID
        try {
            responseData = URL(getActivity).readText()

        } catch (e: Exception) {
            return
        }

        var jsonObj: JSONObject?
        try {
            jsonObj = JSONObject(responseData)
        } catch (e: Exception) {
            return
        }

        this.SanghaTalkHistoryAlbum = ArrayList<TalkData>()
        this.SanghaShareHistoryAlbum = ArrayList<TalkData>()

        // compute talk history
        var talkCount = 0
        var totalSeconds = 0

        var talkJSONList: JSONArray = JSONArray()
        try {
            talkJSONList = jsonObj.getJSONArray("sangha_history")
        } catch (e: Exception) {
        }

        for (i in 0..talkJSONList.length() - 1) {
            val history = talkJSONList.getJSONObject(i)

            var fileName = history.getArg("filename") ?: ""
            var date = history.getArg("date") ?: ""
            var city = history.getArg("city") ?: ""
            var state = history.getArg("state") ?: ""
            var country = history.getArg("country") ?: ""

            if (this.FileNameToTalk.containsKey(fileName) == false) continue
            var talk = this.FileNameToTalk[fileName]

            if (talk != null) {

                val talkHistory = talk.copy(Date = date, CityPlayed = city, StatePlayed = state, CountryPlayed = country)
                //val talkHistory = TalkHistoryData(fileName, datePlayed, timePlayed, city, state, country)
                talkCount += 1
                totalSeconds += talk.DurationInSeconds
                this.SanghaTalkHistoryAlbum.add(talkHistory)

                if (talkCount > MAX_TALKHISTORY_COUNT) {
                    break
                }
            }
        }
        var durationDisplay = this.secondsToDurationDisplay(totalSeconds)
        var stats = AlbumStats(talkCount, totalSeconds, durationDisplay)
        this.SanghaTalkHistoryStats = stats


        // compute share history
        talkCount = 0
        totalSeconds = 0

        talkJSONList = JSONArray()
        try {
            talkJSONList = jsonObj.getJSONArray("sangha_shares")
        } catch (e: Exception) {
        }

        for (i in 0..(talkJSONList.length() - 1)) {

            val history = talkJSONList.getJSONObject(i)

            var fileName = history.getArg("filename") ?: ""
            var city = history.getArg("city") ?: ""
            var date = history.getArg("date") ?: ""
            var state = history.getArg("state") ?: ""
            var country = history.getArg("country") ?: ""

            if (this.FileNameToTalk.containsKey(fileName) == false) continue

            var talk = this.FileNameToTalk[fileName]
            if (talk != null) {

                val talkHistory = talk.copy(Date = date, CityPlayed = city, StatePlayed = state, CountryPlayed = country)
                talkCount += 1
                totalSeconds += talk.DurationInSeconds
                this.SanghaShareHistoryAlbum.add(talkHistory)

                if (talkCount > MAX_TALKHISTORY_COUNT) {
                    break
                }
            }
        }
        durationDisplay = this.secondsToDurationDisplay(totalSeconds)
        stats = AlbumStats(talkCount, totalSeconds, durationDisplay)
        this.SanghaShareHistoryStats = stats


        // lastly get the pluggable DATA albums.  these are optional
        for (dataContent in DATA_ALBUMS) {

            talkCount = 0
            totalSeconds = 0

            this.KeyToTalks[dataContent] = arrayListOf()

            try {
                talkJSONList = jsonObj.getJSONArray(dataContent)
            } catch (e: Exception) {
                continue
            }

            for (i in 0..(talkJSONList.length() - 1)) {

                val history = talkJSONList.getJSONObject(i)
                var fileName = history.getArg("filename") ?: ""
                if (this.FileNameToTalk.containsKey(fileName) == false) continue

                var talk = this.FileNameToTalk[fileName]
                if (talk != null) {

                    talkCount += 1
                    totalSeconds += talk.DurationInSeconds
                    this.KeyToTalks[dataContent]?.add(talk)
                }
            }

            durationDisplay = this.secondsToDurationDisplay(totalSeconds)
            stats = AlbumStats(talkCount, totalSeconds, durationDisplay)
            this.KeyToAlbumStats[dataContent] = stats
        }
    }

    // timer:  load sangha (community) information every UPDATE_SANGHA_INTERVAL seconds
    fun startSanghaHistoryTimer(): Timer {

        val interval: Long = UPDATE_SANGHA_INTERVAL.toLong() * 1000

        val timer = fixedRateTimer(name = "UPDATE_SANGHA_INTERVAL", daemon = true, initialDelay = interval, period = interval) {

            //println("AD UPDATE_SANGHA_INTERVAL")

            downloadSanghaActivity()

            var message = TalkRefreshHandler?.obtainMessage(1)
            message?.sendToTarget()

            message = AlbumRefreshHandler?.obtainMessage(1)
            message?.sendToTarget()
        }
        return timer
    }


    fun downloadMP3(talk: TalkData) : Boolean {


        //"downloadMP3".LOG()

        // remote source path for file
        var requestURL = getFullTalkURL(talk)
        if (remoteURLExists(requestURL) == false) {return false}

        // local destination path for file
        var localPathMP3 = MP3_DOWNLOADS_PATH + "/" + talk.FileName

        this.UserDownloads[talk.FileName] = false

        var responseData = URL(requestURL).readBytes()
        var inputStream: InputStream = responseData.inputStream()

        var outFile = File(localPathMP3)
        outFile.setReadable(true, false)
        var outputStream = FileOutputStream(outFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        this.UserDownloads[talk.FileName] = true
        this.saveUserDownloadData()

        return true
    }


    fun reportTalkActivity(type: ACTIVITIES, talk: TalkData) {

        var operation: String
        when (type) {

            ACTIVITIES.SHARE_TALK -> operation = "SHARETALK"
            ACTIVITIES.PLAY_TALK -> operation = "PLAYTALK"
        }

        val city = TheUserLocation.city
        val state = TheUserLocation.state
        val country = TheUserLocation.country
        val zip = TheUserLocation.zip
        val altitude = TheUserLocation.altitude
        val latitude = TheUserLocation.latitude
        val longitude = TheUserLocation.longitude
        val deviceType = "android"

        var terms = talk.URL.split("/")
        var fileName = terms.last()

        val parameters = "DEVICETYPE=$deviceType&DEVICEID=$DEVICE_ID&OPERATION=$operation&FILENAME=$fileName&CITY=$city&STATE=$state&COUNTRY=$country&ZIP=$zip&ALTITUDE=$altitude&LATITUDE=$latitude&LONGITUDE=$longitude"
        var url = URL_REPORT_ACTIVITY + '?' + parameters

        url.httpPut().responseString { _, _, _ -> }
    }


    fun isInternetAvailable(): Boolean {

        val activeNetwork = ConnectivityService?.activeNetworkInfo
        val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting

        return isConnected
    }


    fun computeRootAlbumStats() {

        for (album in RootAlbum) {

            val talksInAlbum = this.getTalks(album.Content, "")
            val talkCount = talksInAlbum.count()

            var totalSeconds = 0
            for (talk in talksInAlbum) {
                totalSeconds += talk.DurationInSeconds
            }

            val durationDisplay = this.secondsToDurationDisplay(totalTimeInSeconds = totalSeconds)

            val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)
            this.KeyToAlbumStats[album.Content] = stats
        }
    }


    fun computeUserAlbumStats() {

        var totalUserListCount = 0
        var totalUserTalkSecondsCount = 0

        for ((_, album) in UserAlbums) {

            var talkCount = 0
            var totalSeconds = 0
            for (talkName in album.TalkFileNames) {
                val talk = FileNameToTalk[talkName]
                if (talk != null) {
                    totalSeconds += talk.DurationInSeconds
                    talkCount += 1
                }
            }

            totalUserListCount += 1
            totalUserTalkSecondsCount += totalSeconds

            val durationDisplay = secondsToDurationDisplay(totalSeconds)
            val stats = AlbumStats(talkCount, totalSeconds, durationDisplay)

            KeyToAlbumStats[album.Content] = stats
        }

        val durationDisplayAllLists = secondsToDurationDisplay(totalTimeInSeconds = totalUserTalkSecondsCount)
        val stats = AlbumStats(totalTalks = totalUserListCount, totalSeconds = totalUserTalkSecondsCount, durationDisplay = durationDisplayAllLists)

        KeyToAlbumStats[KEY_USER_ALBUMS] = stats

    }


    fun computeSpeakerStats() {

        var totalSecondsAllLists = 0
        var talkCountAllLists = 0

        for (album in SpeakerAlbums) {

            var talkCount = 0
            var totalSeconds = 0
            val content = album.Content

            var talkList = KeyToTalks[content]
            if (talkList != null) {
                for (talk in talkList) {
                    totalSeconds += talk.DurationInSeconds
                    talkCount += 1
                }
            }

            talkCountAllLists += talkCount
            totalSecondsAllLists += totalSeconds
            val durationDisplay = secondsToDurationDisplay(totalSeconds)

            val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)
            KeyToAlbumStats[content] = stats
        }

        val durationDisplayAllLists = secondsToDurationDisplay(totalSecondsAllLists)

        val stats = AlbumStats(totalTalks = talkCountAllLists, totalSeconds = totalSecondsAllLists, durationDisplay = durationDisplayAllLists)
        KeyToAlbumStats[KEY_ALLSPEAKERS] = stats

    }


    fun computeSeriesStats() {

        var totalSecondsAllLists = 0
        var talkCountAllLists = 0

        for (album in SeriesAlbums) {

            val content = album.Content
            var totalSeconds = 0
            var talkCount = 0

            var talkList = KeyToTalks[content]
            if (talkList != null) {
                for (talk in talkList) {
                    totalSeconds += talk.DurationInSeconds
                    talkCount += 1
                }
            }

            talkCountAllLists += talkCount
            totalSecondsAllLists += totalSeconds
            val durationDisplay = secondsToDurationDisplay(totalSeconds)

            val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)
            KeyToAlbumStats[content] = stats
        }

        val durationDisplayAllLists = secondsToDurationDisplay(totalSecondsAllLists)

        val stats = AlbumStats(totalTalks = talkCountAllLists, totalSeconds = totalSecondsAllLists, durationDisplay = durationDisplayAllLists)
        KeyToAlbumStats[KEY_ALL_SERIES] = stats
    }


    fun computeRecommendedStats() {

        var totalSecondsAllLists = 0
        var talkCountAllLists = 0

        for (album in RecommendedAlbums) {

            var talkCount = 0
            var totalSeconds = 0
            val content = album.Content

            var talkList = KeyToTalks[content]
            if (talkList != null) {
                for (talk in talkList) {
                    totalSeconds += talk.DurationInSeconds
                    talkCount += 1
                }
            }

            talkCountAllLists += talkCount
            totalSecondsAllLists += totalSeconds
            val durationDisplay = secondsToDurationDisplay(totalSeconds)

            val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)
            KeyToAlbumStats[content] = stats
        }

        val durationDisplayAllLists = secondsToDurationDisplay(totalSecondsAllLists)

        val stats = AlbumStats(totalTalks = talkCountAllLists, totalSeconds = totalSecondsAllLists, durationDisplay = durationDisplayAllLists)
        KeyToAlbumStats[KEY_RECOMMENDED_TALKS] = stats
    }


    fun computeNotesStats() {

        var talkCount = 0
        var totalSeconds = 0

        for ((fileName, _) in UserNotes) {

            val talk = FileNameToTalk[fileName]
            if (talk != null) {
                totalSeconds += talk.DurationInSeconds
                talkCount += 1
            }
        }
        val durationDisplay = secondsToDurationDisplay(totalSeconds)
        val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

        KeyToAlbumStats[KEY_NOTES] = stats
    }

    fun computeTalkHistoryStats() {

        var talkCount = 0
        var totalSeconds = 0

        for (talk in UserTalkHistoryAlbum) {

            totalSeconds += talk.DurationInSeconds
            talkCount += 1
        }
        val durationDisplay = secondsToDurationDisplay(totalSeconds)
        val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

        KeyToAlbumStats[KEY_USER_TALKHISTORY] = stats
    }


    fun computeShareHistoryStats() {

        var talkCount = 0
        var totalSeconds = 0

        for (talk in UserShareHistoryAlbum) {

            totalSeconds += talk.DurationInSeconds
            talkCount += 1
        }
        val durationDisplay = secondsToDurationDisplay(totalSeconds)
        val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

        KeyToAlbumStats[KEY_USER_SHAREHISTORY] = stats
    }

    fun computeDataStats() {

        for (dataContent in DATA_ALBUMS) {
            var talkCount = 0
            var totalSeconds = 0

            for (talk in KeyToTalks[dataContent]!!) {

                totalSeconds += talk.DurationInSeconds
                talkCount += 1
            }
            val durationDisplay = secondsToDurationDisplay(totalSeconds)
            val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

            KeyToAlbumStats[dataContent] = stats
        }
    }

    fun computeUserFavoritesStats() {

        var talkCount = 0
        var totalSeconds = 0

        for ((fileName, _) in UserFavorites) {

            val talk = FileNameToTalk[fileName]
            if (talk != null) {
                totalSeconds += talk.DurationInSeconds
                talkCount += 1
            }
        }
        val durationDisplay = secondsToDurationDisplay(totalSeconds)
        val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

        KeyToAlbumStats[KEY_USER_FAVORITES] = stats
    }


    fun computeUserDownloadStats() {

        var talkCount = 0
        var totalSeconds = 0

        for ((fileName, _) in UserDownloads) {

            val talk = FileNameToTalk[fileName]
            if (talk != null) {
                totalSeconds += talk.DurationInSeconds
                talkCount += 1
            }
        }
        val durationDisplay = secondsToDurationDisplay(totalSeconds)
        val stats = AlbumStats(totalTalks = talkCount, totalSeconds = totalSeconds, durationDisplay = durationDisplay)

        KeyToAlbumStats[KEY_USER_DOWNLOADS] = stats
    }


    fun saveUserAlbumData() {

        var fileNameList = ""
        for ((albumName, album) in UserAlbums) {

            var filteredAlbumName = "[ALBUMNAME=]" + albumName.replace(STORAGE_DELIMITER, "")
            fileNameList += filteredAlbumName
            fileNameList += STORAGE_DELIMITER

            for (talkFileName in album.TalkFileNames) {

                var talk = FileNameToTalk[talkFileName]
                if (talk != null) {
                    fileNameList += talkFileName
                    fileNameList += STORAGE_DELIMITER
                }
            }
        }

        val prefs = StorageHandle?.edit()
        prefs?.putString("USER_ALBUMS", fileNameList)
        prefs?.apply()

    }


    fun loadUserAlbumData(): HashMap<String, UserAlbumData> {

        val value = StorageHandle?.getString("USER_ALBUMS", "")

        var userAlbums: HashMap<String, UserAlbumData> = hashMapOf()

        if (value == null) return userAlbums
        if (value.count() < STORAGE_MIN) return userAlbums


        var albumName: String
        var fileNameList = value.split(STORAGE_DELIMITER) as ArrayList<String>
        var content: String = "0"

        for (fileName in fileNameList) {

            if (fileName.contains("[ALBUMNAME=]")) {

                content = Random().nextInt().toString()

                albumName = fileName.replace("[ALBUMNAME=]", "")
                val album = UserAlbumData(Title = albumName, TalkFileNames = arrayListOf(), Content = content)
                userAlbums[content] = album

            } else {
                if ((fileName.count() > 1) and (FileNameToTalk.containsKey(fileName))) {

                    if (userAlbums.containsKey(content)) {
                        userAlbums[content]?.TalkFileNames?.add(fileName)
                    }

                }
            }
        }
        return userAlbums
    }

    fun setDeviceID() {

        DEVICE_ID = StorageHandle?.getString("DEVICE_ID", null)
        if (DEVICE_ID == null) {

            DEVICE_ID = UUID.randomUUID().toString()
            val storageEdit = StorageHandle?.edit()
            storageEdit?.putString("DEVICE_ID", DEVICE_ID)
            storageEdit?.apply()

        }
        DEVICE_ID?.LOG()
    }


    fun saveUserDownloadData() {

        var fileNameList = ""
        for ((key, completed) in UserDownloads) {

            var fileName = key.replace(STORAGE_DELIMITER, "")
            if (completed == true) {
                fileNameList += fileName
                fileNameList += STORAGE_DELIMITER
            }
        }

        val storageEdit = StorageHandle?.edit()
        storageEdit?.putString("USER_DOWNLOADS", fileNameList)
        storageEdit?.apply()
    }


    fun loadUserDownloadData(): HashMap<String, Boolean> {

        val value = StorageHandle?.getString("USER_DOWNLOADS", "")

        var userDownloads: HashMap<String, Boolean> = hashMapOf()

        if (value == null) return userDownloads
        if (value.count() < STORAGE_MIN) return userDownloads

        var fileNameList = value.split(STORAGE_DELIMITER) as ArrayList<String>

        for (fileName in fileNameList) {
            if ((fileName.count() > 1) and (FileNameToTalk.containsKey(fileName))) {
                userDownloads.put(fileName, true)
            }
        }

        return userDownloads
    }


    fun saveTalkHistoryData() {

        var fileNameList = ""
        for (talk in UserTalkHistoryAlbum) {

            var fileName = talk.FileName.replace(STORAGE_DELIMITER, "")

            if (FileNameToTalk.containsKey(fileName)) {
                fileNameList += fileName
                fileNameList += STORAGE_DELIMITER
            }
        }

        val storageEdit = StorageHandle?.edit()
        storageEdit?.putString("USER_TALKHISTORY", fileNameList)
        storageEdit?.apply()
    }


    fun loadTalkHistoryData(): ArrayList<TalkData> {

        val value = StorageHandle?.getString("USER_TALKHISTORY", "")

        var talkHistoryList: ArrayList<TalkData> = arrayListOf()
        if (value == null) return talkHistoryList

        if (value.count() < STORAGE_MIN) {
            return talkHistoryList
        }

        var talkFileNameList = value.split(STORAGE_DELIMITER) as ArrayList<String>

        for (talkFileName in talkFileNameList) {

            val talk = FileNameToTalk[talkFileName]
            if (talk != null) {
                talkHistoryList.add(talk)
            }
        }

        return talkHistoryList
    }


    fun saveShareHistoryData() {

        var fileNameList = ""
        for (talk in UserShareHistoryAlbum) {

            var fileName = talk.FileName.replace(STORAGE_DELIMITER, "")

            if (FileNameToTalk.containsKey(fileName)) {
                fileNameList += fileName
                fileNameList += STORAGE_DELIMITER
            }
        }

        val storageEdit = StorageHandle?.edit()
        storageEdit?.putString("USER_SHAREHISTORY", fileNameList)
        storageEdit?.apply()
    }


    fun loadShareHistoryData(): ArrayList<TalkData> {

        val value = StorageHandle?.getString("USER_SHAREHISTORY", "")

        var talkHistoryList: ArrayList<TalkData> = arrayListOf()
        if (value == null) return talkHistoryList

        if (value.count() < STORAGE_MIN) return talkHistoryList

        var talkFileNameList = value.split(STORAGE_DELIMITER) as ArrayList<String>

        for (talkFileName in talkFileNameList) {

            val talk = FileNameToTalk[talkFileName]
            if (talk != null) {
                talkHistoryList.add(talk)
            }
        }

        return talkHistoryList
    }


    fun saveUserNoteData() {

        var fileNameList = ""
        for ((key, userNote) in UserNotes) {

            var fileName = key.replace(STORAGE_DELIMITER, "")
            var note = userNote.replace(STORAGE_DELIMITER, "")

            if ((note.count() > 0) and (FileNameToTalk.containsKey(fileName))) {
                fileNameList += fileName
                fileNameList += STORAGE_DELIMITER
                fileNameList += note
                fileNameList += STORAGE_DELIMITER
            }
        }

        val storageEdit = StorageHandle?.edit()
        storageEdit?.putString("USER_NOTES", fileNameList)
        storageEdit?.apply()
    }


    fun loadUserNoteData(): HashMap<String, String> {

        val value = StorageHandle?.getString("USER_NOTES", "")

        var userNotes: HashMap<String, String> = hashMapOf()
        var userNoteList: ArrayList<String> = arrayListOf()
        if (value == null) return userNotes

        if (value.count() > STORAGE_MIN) {
            userNoteList = value.split(STORAGE_DELIMITER) as ArrayList<String>
        }

        val count = userNoteList.count()
        for (i in 0..count - 1 step 2) {
            var note: String = ""

            var fileName = userNoteList[i]
            if (i + 1 < count) {
                note = userNoteList[i + 1]
            }
            if ((note.count() > 0) and (FileNameToTalk.containsKey(fileName))) {

                userNotes[fileName] = note
            }
        }

        return userNotes
    }


    fun saveUserFavoritesData() {

        var fileNameList = ""
        for ((key, isFavorite) in UserFavorites) {

            var fileName = key.replace(STORAGE_DELIMITER, "")
            if (isFavorite == true) {
                fileNameList += fileName
                fileNameList += STORAGE_DELIMITER
            }
        }

        val storageEdit = StorageHandle?.edit()
        storageEdit?.putString("USER_FAVORITES", fileNameList)
        storageEdit?.apply()

    }


    fun loadUserFavoriteData(): HashMap<String, Boolean> {

        val value = StorageHandle?.getString("USER_FAVORITES", "")

        var userFavorites: HashMap<String, Boolean> = hashMapOf()
        var fileNameList: ArrayList<String> = arrayListOf()
        if (value == null) return userFavorites

        if (value.count() > STORAGE_MIN) {
            fileNameList = value.split(STORAGE_DELIMITER) as ArrayList<String>
        }

        for (fileName in fileNameList) {
            if ((fileName.count() > 1) and (FileNameToTalk.containsKey(fileName))) {
                userFavorites.put(fileName, true)
            }
        }

        return userFavorites
    }


    // ensure that no download records get persisted that are incomplete in any way
    // I do this because asynchronous downloads might not complete, leaving systen in inconsistent state
    // this boot-time check ensures data remains stable, hopefully

    fun validateUserDownloadData() {

        // Prune:
        // 1) Any entry that isn't marked complete
        // 2) Any entry that doesn't have a file associated with it
        var badDownloads: ArrayList<String> = arrayListOf()

        // 1) Enumerate UserDownloads.  If incomplete, remove matching downloads file if any
        for ((fileName, active) in UserDownloads) {

            if (active == false) {
                badDownloads.add(fileName)
            }

            var localPathMP3 = MP3_DOWNLOADS_PATH + "/" + fileName
            if (File(localPathMP3).exists() == false) {

                badDownloads.add(fileName)
            }
        }
        for (fileName in badDownloads) {

            UserDownloads.remove(fileName)
            val localPathMP3 = MP3_DOWNLOADS_PATH + "/" + fileName

            File(localPathMP3).delete()
        }

        // 2) Go the other way: enumerate all downloaded files and delete any that aren't in UserDownloads
        var downloadedFiles = File(MP3_DOWNLOADS_PATH).listFiles()
        for (file in downloadedFiles) {

            val fileName = getFileNameFromPath(file.path)
            if ((UserDownloads.containsKey(fileName)) == false) {

                file.delete()
            }
        }

/*
        downloadedFiles = File(MP3_DOWNLOADS_PATH).listFiles()
        for (file in downloadedFiles) {

            val fileName = getFileNameFromPath(file.path)
        }
*/

        saveUserDownloadData()
    }


    fun getUserAlbums(): ArrayList<UserAlbumData> {

        var userAlbumList: ArrayList<UserAlbumData> = arrayListOf()
        for ((_, album) in UserAlbums) {

            userAlbumList.add(album)
        }
        userAlbumList.sortedWith(compareBy({ it.Title }))
        return (userAlbumList)
    }


    fun getAlbums(content: String, query: String): ArrayList<AlbumData> {

        var albumList: ArrayList<AlbumData> = arrayListOf()

        when (content) {

            KEY_ALBUMROOT -> albumList = RootAlbum
            KEY_ALLSPEAKERS -> albumList = SpeakerAlbums
            KEY_ALL_SERIES -> albumList = SeriesAlbums
            KEY_RECOMMENDED_TALKS -> albumList = RecommendedAlbums

            else -> {
            }
        }

        if (query != "") {

            var filteredAlbumList: ArrayList<AlbumData> = arrayListOf()

            var filter = query.toLowerCase()
            for (album in albumList) {

                val searchData = album.Title.toLowerCase()

                if (searchData.contains(filter)) filteredAlbumList.add(album)
            }
            albumList = filteredAlbumList

        }

        return albumList
    }


    fun getTalks(content: String, query: String): ArrayList<TalkData> {

        var talkList: ArrayList<TalkData> = arrayListOf()

        when (content) {

            KEY_ALLTALKS -> {
                talkList = AllTalks
            }
            KEY_DHARMETTES -> {
                talkList = KeyToTalks["SERIESDharmettes"] ?: arrayListOf()
            }
            KEY_ALL_SERIES -> {

            }
            KEY_USER_DOWNLOADS -> {
                var talks: ArrayList<TalkData> = arrayListOf()

                for ((fileName, _) in UserDownloads) {

                    val talk = FileNameToTalk[fileName]
                    if (talk != null) {
                        talks.add(talk)
                    }
                }
                talkList = talks
            }
            KEY_USER_FAVORITES -> {
                var talks: ArrayList<TalkData> = arrayListOf()

                for ((fileName, _) in UserFavorites) {

                    val talk = FileNameToTalk[fileName]
                    if (talk != null) {
                        talks.add(talk)
                    }
                }
                talkList = talks

            }
            KEY_NOTES -> {
                var talks: ArrayList<TalkData> = arrayListOf()

                for ((fileName, _) in UserNotes) {

                    val talk = FileNameToTalk[fileName]
                    if (talk != null) {
                        talks.add(talk)
                    }
                }
                talkList = talks

            }
            KEY_USER_TALKHISTORY -> {

                talkList = UserTalkHistoryAlbum
                Collections.reverse(talkList)
            }
            KEY_USER_SHAREHISTORY -> {

                talkList = UserShareHistoryAlbum
                Collections.reverse(talkList)

            }
            KEY_SANGHA_SHAREHISTORY -> talkList = SanghaShareHistoryAlbum
            KEY_SANGHA_TALKHISTORY -> talkList = SanghaTalkHistoryAlbum

            else -> talkList = KeyToTalks[content] ?: arrayListOf()

        }

        if (query != "") {

            var filteredTalkList: ArrayList<TalkData> = arrayListOf()

            var filter = query.toLowerCase()
            for (talk in talkList) {

                val notes = TheDataModel.getNoteForTalk(talk.FileName).toLowerCase()
                val searchData = talk.Title.toLowerCase() + " " + talk.Speaker.toLowerCase() + " " +
                        talk.Date + " " + talk.Keys.toLowerCase() + " " + notes

                if (searchData.contains(filter)) {
                    filteredTalkList.add(talk)
                }
            }
            talkList = filteredTalkList
        }

        return talkList
    }


    fun getAlbumStats(content: String): AlbumStats {

        var stats: AlbumStats?

        when (content) {

            KEY_ALLTALKS -> stats = KeyToAlbumStats[content]

            KEY_SANGHA_TALKHISTORY -> stats = SanghaTalkHistoryStats

            KEY_SANGHA_SHAREHISTORY -> stats = SanghaShareHistoryStats

            else -> stats = KeyToAlbumStats[content]
        }

        if (stats == null) stats = AlbumStats(0, 0, "0:0:0")

        return stats
    }


    fun updateUserAlbum(updatedAlbum: UserAlbumData) {

        val content = updatedAlbum.Content
        if (UserAlbums.containsKey(content)) {
            UserAlbums[content] = updatedAlbum
        }

        saveUserAlbumData()
        computeUserAlbumStats()
    }


    fun addUserAlbum(album: UserAlbumData) {

        UserAlbums[album.Content] = album

        saveUserAlbumData()
        computeUserAlbumStats()
    }


    fun removeUserAlbum(album: UserAlbumData) {

        UserAlbums.remove(album.Content)

        saveUserAlbumData()
        computeUserAlbumStats()
    }


    fun getUserAlbumTalks(userAlbum: UserAlbumData): ArrayList<TalkData> {

        var userAlbumTalks: ArrayList<TalkData> = arrayListOf()

        userAlbum.TalkFileNames.forEachIndexed { _, talkFileName ->

            val talk = FileNameToTalk[talkFileName]
            if (talk != null) {
                userAlbumTalks.add(talk)
            }
        }

        return userAlbumTalks
    }


    fun saveUserAlbumTalks(userAlbum: UserAlbumData, talks: ArrayList<TalkData>) {


        var talkFileNames: ArrayList<String> = arrayListOf()
        for (talk in talks) {
            talkFileNames.add(talk.FileName)
        }

        // save the resulting array into the userlist and then persist into storage
        UserAlbums[userAlbum.Content]?.TalkFileNames = talkFileNames

        saveUserAlbumData()
        computeUserAlbumStats()

    }


    fun isMostRecentTalk(talk: TalkData): Boolean {

        if (UserTalkHistoryAlbum.isEmpty()) return false
        if (UserTalkHistoryAlbum.last().FileName != talk.FileName) return false

        return true
    }


    fun addToTalkHistory(talk: TalkData) {

        UserTalkHistoryAlbum.add(talk)

        val excessTalkCount = UserTalkHistoryAlbum.count() - MAX_USERTALKHISTORY_COUNT
        if (excessTalkCount > 0) {
            for (i in 0..excessTalkCount) {
                UserTalkHistoryAlbum.removeAt(0)
            }
        }

        saveTalkHistoryData()
        computeTalkHistoryStats()
    }


    fun addToShareHistory(talk: TalkData) {

        UserShareHistoryAlbum.add(talk)

        val excessTalkCount = UserShareHistoryAlbum.count() - MAX_USERSHAREHISTORY_COUNT
        if (excessTalkCount > 0) {
            for (i in 0..excessTalkCount) {
                UserShareHistoryAlbum.removeAt(0)
            }
        }

        saveShareHistoryData()
        computeShareHistoryStats()
    }


    fun setTalkAsFavorite(talk: TalkData) {

        UserFavorites[talk.FileName] = true
        saveUserFavoritesData()
        computeUserFavoritesStats()
    }

    fun unsetTalkAsFavorite(talk: TalkData) {

        UserFavorites.remove(talk.FileName)
        saveUserFavoritesData()
        computeUserFavoritesStats()
    }


    fun isFavoriteTalk(talk: TalkData): Boolean {

        val isFavorite = (UserFavorites[talk.FileName] != null)
        return isFavorite
    }


    fun setTalkAsDownload(talk: TalkData) {

        UserDownloads[talk.FileName] = false
        saveUserDownloadData()
        computeUserDownloadStats()
    }


    fun unsetTalkAsDownload(talk: TalkData) {

        UserDownloads.remove(talk.FileName)
        saveUserDownloadData()
        computeUserDownloadStats()
    }

    fun isDownloadTalk(talk: TalkData): Boolean {

        return UserDownloads.containsKey(talk.FileName)
    }


    fun isCompletedDownloadTalk(talk: TalkData): Boolean {

        var downloadComplete = false
        if (UserDownloads.containsKey(talk.FileName)) {
            downloadComplete = (UserDownloads[talk.FileName] == true)
        }
        return downloadComplete
    }


    fun isDownloadInProgress(talk: TalkData): Boolean {

        var downloadInProgress = false
        if (UserDownloads.containsKey(talk.FileName)) {
            downloadInProgress = (UserDownloads[talk.FileName] == false)
        }
        return downloadInProgress
    }


    fun addNoteToTalk(noteText: String, talkFileName: String) {

        //
        // if there is a note text for this talk fileName, then save it in the note dictionary
        // otherwise clear this note dictionary entry
        if (noteText.count() > 0) {
            UserNotes[talkFileName] = noteText

        } else {
            UserNotes.remove(talkFileName)
        }

        // save the data, recompute stats, reload root view to display updated stats
        saveUserNoteData()
        computeNotesStats()
    }


    fun getNoteForTalk(talkFileName: String): String {

        var userNote = UserNotes[talkFileName]

        if (userNote == null) userNote = ""

        return userNote
    }


    fun isNotatedTalk(talk: TalkData): Boolean {

        return (UserNotes.containsKey(talk.FileName))
    }


    fun secondsToDurationDisplay(totalTimeInSeconds: Int): String {

        val hours = totalTimeInSeconds / 3600
        val modHours = totalTimeInSeconds % 3600
        val minutes = modHours / 60
        val seconds = modHours % 60

        val hoursStr = String.format("%02d", hours)
        val minutesStr = String.format("%02d", minutes)
        val secondsStr = String.format("%02d", seconds)

        return hoursStr + ":" + minutesStr + ":" + secondsStr
    }


    fun convertDurationToSeconds(duration: String): Int {

        var totalSeconds: Int
        var hours: Int = 0
        var minutes: Int = 0
        var seconds: Int = 0
        if (duration != "") {
            val durationArray = duration.split(":")
            val count = durationArray.count()
            if (count == 3) {
                hours = durationArray[0].toInt()
                minutes = durationArray[1].toInt()
                seconds = durationArray[2].toInt()
            } else if (count == 2) {
                hours = 0
                minutes = durationArray[0].toInt()
                seconds = durationArray[1].toInt()
            } else if (count == 1) {
                hours = 0
                minutes = 0
                seconds = durationArray[0].toInt()

            } else {
            }
        }
        totalSeconds = (hours * 3600) + (minutes * 60) + seconds
        return totalSeconds
    }


    fun deviceRemainingFreeSpaceInBytes(): Long {

        val stat = StatFs(Environment.getExternalStorageDirectory().getPath())
        val bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong()
        //val megAvailable = bytesAvailable / 1048576;
        return bytesAvailable
    }


    fun doesTalkHaveTranscript(talk: TalkData): Boolean {

        if (talk.PDF.toLowerCase().contains("http:")) return true
        if (talk.PDF.toLowerCase().contains("https:")) return true
        return false
    }


    fun isFullURL(url: String): Boolean {

        if (url.toLowerCase().contains("http:")) return true
        if (url.toLowerCase().contains("https:")) return true
        return false
    }


    fun getFileNameFromPath(url: String): String {

        val terms = url.split(delimiters = "/")
        return terms.last()
    }

    fun getTalkForFileName(fileName: String?): TalkData? {

        if (FileNameToTalk.containsKey(fileName))
            return FileNameToTalk[fileName]

        return null
    }

    fun remoteURLExists(URLName: String): Boolean {

        println("AD: remoteURLExists")
        println(URLName)
        try {
            HttpURLConnection.setFollowRedirects(true)

            val con = URL(URLName).openConnection() as HttpURLConnection
            con.setRequestMethod("HEAD")
            var code: Int = con.getResponseCode()
            //println(code)

            if (code == 404)
                return false
            else
                return true
        } catch (e: Exception) {
            //e.printStackTrace()
        }

        return false


    }

    fun getFullTalkURL(talk: TalkData): String {

        var talkURL: String

        if (TheDataModel.isFullURL(talk.URL))
        {
            talkURL = talk.URL
        }
        else if (USE_NATIVE_MP3PATHS == true)
        {
            talkURL = URL_MP3_HOST + talk.URL

        } else
        {
            talkURL = URL_MP3_HOST + "/" + talk.FileName
        }

        return talkURL
    }

    //
    // DEV TEST
    //import java.lang.system*
    fun  copyAsset() {
/*
        val documentPath = AppContext?.getExternalFilesDir(null)?.absolutePath
        val outputPath = documentPath + "/DOWNLOADS/test00.mp3"
        var assetManager = AppContext?.assets
        val filename = "test00.mp3"

        try {
            var input = assetManager?.open(filename)
            var outFile =  File(outputPath)
            var output =  FileOutputStream(outFile)
            input?.copyTo(output)
            input?.close()
            output.close()
        } catch(e: IOException ) {
        }
*/
    }



}

