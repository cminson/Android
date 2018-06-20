package com.christopherminson.audiodharma

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.support.v7.widget.SearchView
import android.view.*
import android.app.SearchManager
import android.widget.*
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.PendingIntent.getActivity
import android.graphics.Bitmap
import android.graphics.Color
import android.support.v4.content.ContextCompat
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import android.os.Handler
import android.os.Looper
import android.support.v4.app.ActivityCompat.startActivityForResult
import com.christopherminson.audiodharma.R.id.attribute1
import com.christopherminson.audiodharma.R.id.attribute2

import android.os.Message
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files.find

var SHARE_CHOOSER = 1
var ContentBase = ""


open class AbstractTalkController : AppCompatActivity() {

    var SharedTalk : TalkData? = null
    lateinit var TalkAdapter: AbstractListAdapter
    var IsSearchResults = false
    var ShareResultSuccess = true   // denotes if a share succeeded or not.  see hack below

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater

        if (IsSearchResults == true) {

            inflater.inflate(R.menu.basemenu, menu)

        } else {
            inflater.inflate(R.menu.searchmenu, menu)

            // Associate searchable configuration with the SearchView
            val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
            val searchView = menu.findItem(R.id.search).actionView as SearchView
            searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(componentName))
        }

        return true
    }


    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {
        super.onPause()

        // HACK:  if only onPause is caused, then a full intent didn't happen, which means that
        // a we probably didn't share anything.
        ShareResultSuccess = false
    }


    override fun onStop() {

        super.onStop()

        // HACK:  if  onStop is caused, then a full intent did happen and caused us
        // to stop, which means that we probably didn't share anything.
        ShareResultSuccess = true
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SHARE_CHOOSER) {

            // must do that rather than check resultcode == Activity.RESULT_OK.
            // cuz that doesnt work.
            if (ShareResultSuccess == true) {

                TheDataModel.addToShareHistory(SharedTalk!!)
                TheDataModel.reportTalkActivity(ACTIVITIES.SHARE_TALK, SharedTalk!!)
            }
        }
    }


    fun launchShareIntent (talk: TalkData) {

        SharedTalk = talk

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setType("text/plain")

        val url = TheDataModel.getFullTalkURL(talk)
        val message = "The following talk has been shared from the android Audio Dharma app\n\n$url"
        shareIntent.putExtra(Intent.EXTRA_TEXT, message)
        setResult(RESULT_OK, shareIntent)

        val chooserIntent = Intent.createChooser(shareIntent, "Share This AudioDharma Talk")
        setResult(RESULT_OK, chooserIntent)
        startActivityForResult(chooserIntent, SHARE_CHOOSER)
    }


    fun playTalk(talkDataList: ArrayList<TalkData>, position: Int) {

        // sets the globals the MP3 controller uses
        ResumeTalkMode = false
        PlayTalkList = talkDataList
        PlayTalkIndex = position
        SecondsIntoTalk = 0

        // sanity checks
        if (PlayTalkList.count() == 0) { return }
        if (PlayTalkIndex >= PlayTalkList.count()) { return }

        val intent = Intent(this, MP3Controller::class.java)
        this.startActivity(intent)
    }
}


class TalkController : AbstractTalkController() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        val intent = getIntent()
        var query = ""

        setContentView(R.layout.list_talks)

        val myToolbar = findViewById<View>(R.id.talks_toolbar) as Toolbar
        setSupportActionBar(myToolbar)

        if (Intent.ACTION_SEARCH == intent.action) {

            IsSearchResults = true
            query = intent.getStringExtra(SearchManager.QUERY)
            getSupportActionBar()?.setTitle("Search: " + query)

        } else {

            ContentBase = intent.getStringExtra("CONTENT")
            val title = intent.getStringExtra("TITLE")
            getSupportActionBar()?.setTitle(title)

        }

        TalkAdapter = TalksListAdapter(context = this, content = ContentBase, query = query)
        TalkRefreshHandler = TalkDispatchHandler(Looper.getMainLooper(), TalkAdapter)

    }
}


class TalkHistoryController : AbstractTalkController() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        val intent = getIntent()
        var query = ""

        setContentView(R.layout.list_talks)

        val myToolbar = findViewById<View>(R.id.talks_toolbar) as Toolbar
        setSupportActionBar(myToolbar)

        if (Intent.ACTION_SEARCH == intent.action) {

            IsSearchResults = true
            query = intent.getStringExtra(SearchManager.QUERY)
            getSupportActionBar()?.setTitle("Search: " + query)

        } else {

            IsSearchResults = false

            ContentBase = intent.getStringExtra("CONTENT")
            val title = intent.getStringExtra("TITLE")
            getSupportActionBar()?.setTitle(title)


        }

        TalkAdapter = TalksHistoryListAdapter(context = this, content = ContentBase, query = query)
        TalkRefreshHandler = TalkDispatchHandler(Looper.getMainLooper(), TalkAdapter)
    }


    override fun onPause() {
        super.onPause()


    }


    override fun onResume() {
        super.onResume()

    }

}



abstract class AbstractListAdapter(context: AbstractTalkController, content: String, query: String): BaseAdapter() {

    val Content: String = content
    val ControllerContext: AbstractTalkController = context
    var DisplaySpeakerNamesInTitle = false

    var TalkListData: ArrayList<TalkData>
    val TalkListView: ListView
    var SelectedTalk : TalkData? = null


    init {

        if (content == KEY_ALLTALKS) DisplaySpeakerNamesInTitle = true

        TalkListData = TheDataModel.getTalks(Content, query)
        TalkListView = ControllerContext.findViewById<ListView>(R.id.talks_list)
        TalkListView.adapter = this

        TalkListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->

            val selectedTalk = TalkListData[i]
            if (selectedTalk.Title == SECTION_HEADER) return@OnItemClickListener
            var talkDataList : ArrayList<TalkData> = arrayListOf()

            for (talk in TalkListData) {
                if (talk.Title == SECTION_HEADER) continue
                talkDataList.add(talk)
            }
            if (talkDataList.count() == 0) return@OnItemClickListener

            var position = 0
            for (talk in talkDataList) {
                if (talk == selectedTalk) break;
                position += 1
            }
            if (position >= talkDataList.count()) return@OnItemClickListener

            ControllerContext.playTalk(talkDataList, position)
        }

    }


    override fun getCount(): Int {

        val count = TalkListData.count()
        return count
    }


    override fun getItemId(position: Int): Long {
        return position.toLong()
    }


    override fun getItem(position: Int): Any {
        return ""
    }


    fun showTalkActionMenu(v: View, position: Int, talk: TalkData) {

        val popup = PopupMenu(ControllerContext, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.talkmenu, popup.menu)

        var menuFavorite = popup.menu.findItem(R.id.favorite)
        var menuDownload = popup.menu.findItem(R.id.download)
        var menuNote = popup.menu.findItem(R.id.note)

        if (TheDataModel.isFavoriteTalk(talk)) menuFavorite.setTitle("Delete Favorite")
        if (TheDataModel.isDownloadTalk(talk)) menuDownload.setTitle("Delete Download")
        if (TheDataModel.isNotatedTalk(talk)) menuNote.setTitle("Edit Note")

        popup.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                when (item.itemId) {

                    R.id.favorite -> {

                        if (TheDataModel.isFavoriteTalk(talk)) {
                            unfavoriteTalk(talk)
                        } else {
                            favoriteTalk(talk)
                        }
                        return true
                    }

                    R.id.note -> {

                        displayNoteDialog(talk)
                        return true
                    }

                    R.id.share -> {

                        ControllerContext.launchShareIntent(talk)
                        return true
                    }

                    R.id.download -> {

                        val spaceRequired = talk.DurationInSeconds * MP3_BYTES_PER_SECOND
                        val freeSpace = TheDataModel.deviceRemainingFreeSpaceInBytes()

                        val dialog = AlertDialog.Builder(ControllerContext).create()

                        if (TheDataModel.isDownloadTalk(talk)) {

                            dialog.setTitle("Delete Downloaded Talk?")
                            dialog.setMessage("Delete talk from local storage.")

                            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", { _, _ -> })

                            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ ->
                                TheDataModel.unsetTalkAsDownload(talk)
                                refreshData()
                                notifyDataSetChanged()
                            })


                        } else {

                            if (spaceRequired > freeSpace) {

                                dialog.setTitle("Insufficient Space To Download")
                                dialog.setMessage("You don't have enough space in your device to download this talk.")

                                dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })

                            } else {
                                dialog.setTitle("Download Talk?")
                                dialog.setMessage("Download talk to device storage.\n\nTalk will be listed in your Download Album.")

                                dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ ->
                                    executeDownload(talk)
                                })

                                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", { _, _ -> })
                            }
                        }

                        dialog.show()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#006400"));

                        return true
                    }

                    else -> return false
                }
            }
        })

        popup.show()
    }


    fun executeDownload(talk: TalkData) {

        TheDataModel.setTalkAsDownload(talk)
        notifyDataSetChanged()

        doAsync {
            val exists = TheDataModel.downloadMP3(talk)
            uiThread {

                if (exists == false) {

                    TheDataModel.unsetTalkAsDownload(talk)

                    val dialog = AlertDialog.Builder(ControllerContext).create()
                    dialog.setTitle("All Things Are Transient")
                    dialog.setMessage("This talk is currently unavailable.  It may have been moved or is being updated.  Please try again later.")
                    dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })
                    dialog.show()
                }

                notifyDataSetChanged()

            }
        }
    }


    fun favoriteTalk(talk: TalkData) {

        TheDataModel.setTalkAsFavorite(talk)
        showConfirmationDialog("Favorite Talk - Added", "This talk has been added to your Favorites Album")
        notifyDataSetChanged()
    }


    fun unfavoriteTalk(talk: TalkData) {

        TheDataModel.unsetTalkAsFavorite(talk)
        refreshData()
        showConfirmationDialog("Favorite Talk - Removed", "This talk has been removed from your Favorites Album")
        notifyDataSetChanged()
    }


    fun showConfirmationDialog(title: String, message: String) {

        val dialog = AlertDialog.Builder(ControllerContext).create()
        dialog.setTitle(title)
        dialog.setMessage(message)

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ -> })

        dialog.show()
        //dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GREEN)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#006400"));

    }


    fun displayNoteDialog(talk: TalkData) {

        val dialog = AlertDialog.Builder(ControllerContext).create()
        val inflater = ControllerContext.getLayoutInflater()
        val note_dialog = inflater.inflate(R.layout.dialog_editnote, null)
        val buttonDeleteNote = note_dialog.findViewById<Button>(R.id.buttonDeleteNote)
        val editNote = note_dialog.findViewById<EditText>(R.id.editNote)

        dialog.setView(note_dialog)

        var noteText = TheDataModel.getNoteForTalk(talk.FileName)
        editNote.setSingleLine(false)
        editNote.setLines(2)
        editNote.setMaxLines(2)
        editNote.setGravity(Gravity.LEFT or Gravity.TOP)
        editNote.setHorizontalScrollBarEnabled(false)
        editNote.setText(noteText, TextView.BufferType.EDITABLE);


        dialog.setTitle(talk.Title)

        buttonDeleteNote.setBackgroundColor(Color.RED)
        buttonDeleteNote.setOnClickListener { editNote.setText("") }

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", { _, _ ->

            noteText = editNote.getText().toString()
            TheDataModel.addNoteToTalk(noteText, talk.FileName)
            refreshData()
            notifyDataSetChanged()
        })

        dialog.show()
        //dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GREEN)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#006400"));


    }

    fun refreshData() {

        TalkListData = TheDataModel.getTalks(Content, "")
    }

}


class TalksListAdapter(context: AbstractTalkController, content: String, query: String): AbstractListAdapter(context, content, query) {

    override fun getView(position: Int, p1: View?, viewGroup: ViewGroup?): View {

        val layoutInflator = LayoutInflater.from(ControllerContext)

        val rowSectionHeader = layoutInflator.inflate(R.layout.section_header, viewGroup, false)
        val sectionView = rowSectionHeader.findViewById<TextView>(R.id.section_header)

        var rowMain: View
        rowMain = layoutInflator.inflate(R.layout.row_talk, viewGroup, false)


        val titleTextView = rowMain.findViewById<TextView>(R.id.title)
        val imageView = rowMain.findViewById<ImageView>(R.id.imageView)
        val attribute1 = rowMain.findViewById<TextView>(R.id.attribute1)
        val attribute2 = rowMain.findViewById<TextView>(R.id.attribute2)

        val isFavoriteImage = rowMain.findViewById<ImageView>(R.id.isFavorite)
        val isNoteImage = rowMain.findViewById<ImageView>(R.id.isNote)
        val menuButton = rowMain.findViewById<Button>(R.id.menuButton)

        if (TalkListData[position].Title == SECTION_HEADER) {

            sectionView.text = TalkListData[position].Section
            return rowSectionHeader
        }

        isFavoriteImage.id = position

        SelectedTalk = TalkListData[position]

        menuButton.setOnClickListener { showTalkActionMenu(it as View, position, TalkListData[position]) }

        // attach photo of speaker to talk.  or default photo if no speaker photo exists
        val imageName = SelectedTalk?.Speaker!!.toLowerCase().replace(" ", "")
        var imageResource = ControllerContext.getResources().getIdentifier(imageName, "drawable", ControllerContext.getPackageName())
        if (imageResource == 0) imageResource = ControllerContext.getResources().getIdentifier("@drawable/defaultphoto", null, ControllerContext.getPackageName())
        imageView.setImageDrawable(ContextCompat.getDrawable(ControllerContext, imageResource))

        var title = SelectedTalk?.Title

        if (TheDataModel.isFavoriteTalk(SelectedTalk!!)) isFavoriteImage.setBackgroundResource(R.drawable.ic_favorite)
        else isFavoriteImage.setBackgroundResource(R.drawable.ic_empty)
        if (TheDataModel.isNotatedTalk(SelectedTalk!!)) isNoteImage.setBackgroundResource(R.drawable.ic_note)
        else isNoteImage.setBackgroundResource(R.drawable.ic_empty)

        if (TheDataModel.isDownloadInProgress(SelectedTalk!!)) {

            titleTextView.text = "DOWNLOADING: " + title

        } else {

            if (DisplaySpeakerNamesInTitle == true) {
                titleTextView.text = title + " - " + SelectedTalk?.Speaker
            } else {
                titleTextView.text = title
            }
        }

        if (TheDataModel.isCompletedDownloadTalk(SelectedTalk!!)) {
            titleTextView.setTextColor(BUTTON_DOWNLOAD_COLOR)
        }

        attribute1.text = SelectedTalk?.DurationDisplay
        attribute2.text = SelectedTalk?.Date


        return rowMain
    }

}



class TalksHistoryListAdapter(context: AbstractTalkController, content: String, query: String): AbstractListAdapter(context, content, query) {

    override fun getView(position: Int, p1: View?, viewGroup: ViewGroup?): View {

        val layoutInflator = LayoutInflater.from(ControllerContext)

        val rowSectionHeader = layoutInflator.inflate(R.layout.section_header, viewGroup, false)
        val sectionView = rowSectionHeader.findViewById<TextView>(R.id.section_header)

        var rowMain: View
        rowMain = layoutInflator.inflate(R.layout.row_talkhistory, viewGroup, false)

        val titleTextView = rowMain.findViewById<TextView>(R.id.title)
        val imageView = rowMain.findViewById<ImageView>(R.id.imageView)
        val attribute1 = rowMain.findViewById<TextView>(R.id.attribute1)
        val attribute2 = rowMain.findViewById<TextView>(R.id.attribute2)
        val attribute3 = rowMain.findViewById<TextView>(R.id.attribute3)

        val isFavoriteImage = rowMain.findViewById<ImageView>(R.id.isFavorite)
        val isNoteImage = rowMain.findViewById<ImageView>(R.id.isNote)
        val menuButton = rowMain.findViewById<Button>(R.id.menuButton)

        if (TalkListData[position].Title == SECTION_HEADER) {

            sectionView.text = TalkListData[position].Section
            return rowSectionHeader
        }

        isFavoriteImage.id = position

        SelectedTalk = TalkListData[position]

        menuButton.setOnClickListener { showTalkActionMenu(it as View, position, TalkListData[position]) }

        // attach photo of speaker to talk.  or default photo if no speaker photo exists
        val imageName = SelectedTalk?.Speaker!!.toLowerCase().replace(" ", "")
        var imageResource = ControllerContext.getResources().getIdentifier(imageName, "drawable", ControllerContext.getPackageName())
        if (imageResource == 0) imageResource = ControllerContext.getResources().getIdentifier("@drawable/defaultphoto", null, ControllerContext.getPackageName())
        imageView.setImageDrawable(ContextCompat.getDrawable(ControllerContext, imageResource))

        var title = SelectedTalk?.Title

        if (TheDataModel.isFavoriteTalk(SelectedTalk!!)) isFavoriteImage.setBackgroundResource(R.drawable.ic_favorite)
        else isFavoriteImage.setBackgroundResource(R.drawable.ic_empty)
        if (TheDataModel.isNotatedTalk(SelectedTalk!!)) isNoteImage.setBackgroundResource(R.drawable.ic_note)
        else isNoteImage.setBackgroundResource(R.drawable.ic_empty)

        if (TheDataModel.isDownloadInProgress(SelectedTalk!!)) {

            titleTextView.text = "DOWNLOADING: " + title
        } else {

            titleTextView.text = title
        }

        if (TheDataModel.isCompletedDownloadTalk(SelectedTalk!!)) {
            titleTextView.setTextColor(BUTTON_DOWNLOAD_COLOR)
        }

        attribute1.text = SelectedTalk?.CityPlayed
        attribute2.text = SelectedTalk?.StatePlayed + " " + SelectedTalk?.CountryPlayed
        attribute3.text = SelectedTalk?.Date

        return rowMain
    }

}

class TalkDispatchHandler (looper: Looper, controller: AbstractListAdapter) : Handler(looper) {

    val x = controller

    override fun handleMessage(msg: Message?) {

        when (msg?.what) {
            1 -> {
                x.refreshData()
                x.notifyDataSetChanged()
            }
        }
        super.handleMessage(msg)
    }
}