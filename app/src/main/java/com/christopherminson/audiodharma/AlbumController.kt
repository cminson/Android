package com.christopherminson.audiodharma

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.Intent.getIntent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.christopherminson.audiodharma.R.id.imageView
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.startActivity
import android.support.v7.widget.SearchView

import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import java.text.NumberFormat


var AlbumContentBase : String = ""

open class AbstractAlbumController : AppCompatActivity() {

    open fun gotoController(album: AlbumData) {}
}


class AlbumController : AbstractAlbumController() {

    lateinit var AlbumAdapter: AlbumListAdapter
    var Content = KEY_ALBUMROOT
    var CachedThis = this
    var mToolbar: Toolbar? = null
    var IsSearchResults = false
    var query = ""


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        ConnectivityService = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        setContentView(R.layout.list_albums)
        val myToolbar = findViewById<View>(R.id.album_toolbar) as Toolbar
        setSupportActionBar(myToolbar)

        if (TheDataModel.isInternetAvailable() == true) {
            getSupportActionBar()?.setTitle("Audio Dharma Create")
            //getSupportActionBar()?.setTitle("Audio Dharma Create $TimeToLoadNewData $TotalModelUpdates")

        } else {
            getSupportActionBar()?.setTitle("Audio Dharma Create (Offline)")
        }

        val launch_screen = findViewById<View>(R.id.splashscreen_earth)
        if (TheDataModel.Initialized == false) {

            doAsync {
                TheDataModel.loadData(this@AlbumController)

                uiThread {
                    TheDataModel.Initialized = true

                    AlbumAdapter = AlbumListAdapter(context = this@AlbumController, content = KEY_ALBUMROOT, query = "")
                    AlbumRefreshHandler = AlbumDispatchHandler(Looper.getMainLooper(), AlbumAdapter)
                    launch_screen.visibility = View.GONE
                }
            }

        } else {
            AlbumAdapter = AlbumListAdapter(context = this, content = KEY_ALBUMROOT, query = query)
            AlbumRefreshHandler = AlbumDispatchHandler(Looper.getMainLooper(), AlbumAdapter)

            launch_screen.visibility = View.GONE

        }
    }


    override fun onPause() {
        super.onPause()
    }


    override fun onResume() {
        super.onResume()


        // if this is ever true, it means a timer somewhere set this variable and we should refresh data
        // so bring back up the launch screen and re-perform data load and presentation
        val timeInterval = System.currentTimeMillis() / 1000
        if (timeInterval > LAST_MODEL_UPDATE + UPDATE_MODEL_INTERVAL) {

            if (!TheDataModel.isInternetAvailable()) return

            LAST_MODEL_UPDATE = timeInterval

            val launch_screen = findViewById<View>(R.id.splashscreen_earth)
            launch_screen.visibility = View.VISIBLE

            TheDataModel.resetData()
            doAsync {

                TheDataModel.loadData(this@AlbumController)

                uiThread {
                    TheDataModel.Initialized = true

                    AlbumAdapter = AlbumListAdapter(context = this@AlbumController, content = KEY_ALBUMROOT, query = "")

                    AlbumRefreshHandler = AlbumDispatchHandler(Looper.getMainLooper(), AlbumAdapter)

                    launch_screen.visibility = View.GONE

                    getSupportActionBar()?.setTitle("Audio Dharma")

                }
            }
            return
        }

        // otherwise just reset title and show the view
        if (TheDataModel.isInternetAvailable() == true) getSupportActionBar()?.setTitle("Audio Dharma")
        AlbumAdapter = AlbumListAdapter(context = this, content = KEY_ALBUMROOT, query = "")
        AlbumRefreshHandler = AlbumDispatchHandler(Looper.getMainLooper(), AlbumAdapter)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.basemenu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.action_resumetalk -> {

                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val talkFileName = prefs.getString(KEY_PLAYINGTALK_NAME, "")
                val talk = TheDataModel.FileNameToTalk.get(talkFileName)

                if (talk != null)
                {
                    // sets the globals the MP3 controller uses
                    ResumeTalkMode = true
                    PlayTalkList = arrayListOf(talk)
                    PlayTalkIndex = 0
                    SecondsIntoTalk = prefs.getInt(KEY_PLAYINGTALK_POSITION, 0)

                    intent = Intent(this, MP3Controller::class.java)
                    startActivity(intent)

                } else {

                    val dialog = AlertDialog.Builder(this).create()
                    dialog.setTitle("Go To Your Last Talk")
                    dialog.setMessage("\nYou have not listened to a talk yet. \nTherefore no action was taken.")

                    dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ ->  })

                    dialog.show()

                }

                return true

            } else -> return super.onOptionsItemSelected(item)
        }
    }


    override fun gotoController(album: AlbumData) {

        var intent: Intent?

        when (album.Content) {

            KEY_ALLSPEAKERS -> intent = Intent(this, SubAlbumController::class.java)
            KEY_ALL_SERIES -> intent = Intent(this, SubAlbumController::class.java)
            KEY_RECOMMENDED_TALKS -> intent = Intent(this, SubAlbumController::class.java)
            KEY_SANGHA_TALKHISTORY -> intent = Intent(this, TalkHistoryController::class.java)
            KEY_SANGHA_SHAREHISTORY -> intent = Intent(this, TalkHistoryController::class.java)

            else ->  intent = Intent(this, TalkController::class.java)
        }

        intent.putExtra("CONTENT", album.Content)
        intent.putExtra("TITLE", album.Title)
        startActivity(intent)
    }
}


class SubAlbumController : AbstractAlbumController() {

    lateinit var AlbumAdapter: AlbumListAdapter
    var Content = KEY_ALBUMROOT
    var mToolbar: Toolbar? = null
    var IsSearchResults = false
    var query = ""


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.list_subalbums)
        val myToolbar = findViewById<View>(R.id.album_toolbar) as Toolbar
        setSupportActionBar(myToolbar)

        val intent = getIntent()
        if (Intent.ACTION_SEARCH == intent.action) {

            IsSearchResults = true
            query = intent.getStringExtra(SearchManager.QUERY)
            getSupportActionBar()?.setTitle("Search: " + query)

        } else {

            AlbumContentBase = intent.getStringExtra("CONTENT")
            val title = intent.getStringExtra("TITLE")
            getSupportActionBar()?.setTitle(title)
        }

        AlbumAdapter = AlbumListAdapter(context = this, content = AlbumContentBase, query = query)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        val inflater = menuInflater

        if (IsSearchResults == true) {

            inflater.inflate(R.menu.basemenu, menu)

        } else {
            inflater.inflate(R.menu.searchmenu, menu)

            val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
            val searchView = menu.findItem(R.id.search).actionView as SearchView
            searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        }

        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return super.onOptionsItemSelected(item)
    }


    override fun gotoController(album: AlbumData) {

        val intent = Intent(this, TalkController::class.java)
        intent.putExtra("CONTENT", album.Content)
        intent.putExtra("TITLE", album.Title)
        startActivity(intent)
    }
}



class AlbumListAdapter(context: AbstractAlbumController, content: String, query: String) : BaseAdapter() {

    public var AlbumList = ArrayList<AlbumData>()
    var AlbumListView: ListView
    private val ControllerContext: AbstractAlbumController
    private val Content = content

    init {

        ControllerContext = context

        AlbumList = TheDataModel.getAlbums(content, query)

        AlbumListView = ControllerContext.findViewById<ListView>(R.id.albums_listview)
        AlbumListView.adapter = this
        AlbumListView.setBackgroundColor(Color.WHITE)
        AlbumListView.setVisibility(View.VISIBLE)
        AlbumListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            this.albumSelected(i)
        }
    }


    override fun getCount(): Int {

        val count = AlbumList.count()
        return count
    }

    override fun getItemId(position: Int): Long {

        return position.toLong()
    }

    override fun getItem(position: Int): Any {
        return ""
    }


    override fun getView(position: Int, p1: View?, viewGroup: ViewGroup?): View {

        val layoutInflator = LayoutInflater.from(ControllerContext)
        var rowMain: View

        val album = AlbumList[position]

        if (album.Title == SECTION_HEADER) {

            rowMain = layoutInflator.inflate(R.layout.section_header, viewGroup, false)
            var sectionView = rowMain.findViewById<TextView>(R.id.section_header)
            sectionView.text = album.Section

        } else {

            var imageResource: Int
            var resources: Resources = ControllerContext.getResources()

            rowMain = layoutInflator.inflate(R.layout.row_album, viewGroup, false)
            var titleView = rowMain.findViewById<TextView>(R.id.title)
            var totalCountView = rowMain.findViewById<TextView>(R.id.attribute1)
            var totalDurationView = rowMain.findViewById<TextView>(R.id.attribute2)

            val albumStats = TheDataModel.getAlbumStats(album.Content)

            val formattedTalkCount = NumberFormat.getNumberInstance(Locale.US).format(albumStats.totalTalks)

            titleView.text = album.Title
            totalCountView.text = formattedTalkCount
            totalDurationView.text = albumStats.durationDisplay

            val imageView = rowMain.findViewById<ImageView>(R.id.imageView)

            val defaultURI = "@drawable/defaultphoto"
            if (album.Image.count() > 0) {

                val imageName = album.Image.toLowerCase().replace(" ", "")
                val uri = "@drawable/$imageName"
                imageResource = resources.getIdentifier(uri, null, ControllerContext.getPackageName())
                if (imageResource == 0) {
                    imageResource = resources.getIdentifier(defaultURI, null, ControllerContext.getPackageName())
                }

            } else {

                val imageName = album.Title.toLowerCase().replace(" ", "")

                val uri = "@drawable/$imageName"
                imageResource = resources.getIdentifier(uri, null, ControllerContext.getPackageName())
                if (imageResource == 0) {
                    imageResource = resources.getIdentifier(defaultURI, null, ControllerContext.getPackageName())
                }
            }

            imageView.setImageDrawable(ContextCompat.getDrawable(ControllerContext, imageResource))
        }

        return rowMain
    }

    fun albumSelected(position: Int) {

        var album: AlbumData = AlbumList[position]

        if (album.Title != SECTION_HEADER) {

            ControllerContext.gotoController(album)
        }
    }

    fun refreshData() {

        AlbumList = TheDataModel.getAlbums(Content, "")
    }


}

class AlbumDispatchHandler (looper: Looper, listAdapter: AlbumListAdapter) : Handler(looper) {

    val x = listAdapter

    override fun handleMessage(msg: Message?) {

        when (msg?.what) {
            1 -> {
                x.refreshData()
                x.notifyDataSetChanged()
            }
            else -> super.handleMessage(msg)
        }
    }

    /*

    fun startBackgroundRefresh() {

        val interval: Long = UPDATE_ALBUMVIEW_INTERVAL * 1000

        RefreshHandler = Handler()

        RefreshHandler?.postDelayed(object : Runnable {
            override fun run() {
                try {

                    AlbumAdapter.notifyDataSetChanged()
                } finally {
                    RefreshHandler?.postDelayed(this, interval)
                }
            }
        }, interval)
    }
    */
}



