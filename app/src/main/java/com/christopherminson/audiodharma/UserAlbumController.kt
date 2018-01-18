package com.christopherminson.audiodharma

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.christopherminson.audiodharma.R.id.imageView
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.startActivity

import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.*
import android.widget.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import java.text.NumberFormat


class UserAlbumController : AppCompatActivity() {

    lateinit var AlbumAdapter: UserAlbumListAdapter
    var Content = KEY_USER_ALBUMS
    var mToolbar: Toolbar? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.list_albums)
        val myToolbar = findViewById<View>(R.id.album_toolbar) as Toolbar
        setSupportActionBar(myToolbar)

        getSupportActionBar()?.setTitle("Custom Albums");

        AlbumAdapter = UserAlbumListAdapter(context = this)

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        val inflater = menuInflater
        inflater.inflate(R.menu.useralbumsmenu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {


            R.id.action_newalbum -> {

                this.displayNewCustomAlbumDialog()
                return true
            }

            else ->
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item)
        }
    }

    fun displayNewCustomAlbumDialog() {

        val dialog = AlertDialog.Builder(this).create()
        val inflater = this.getLayoutInflater()
        val dialog_view = inflater.inflate(R.layout.dialog_edituseralbum, null)
        dialog.setView(dialog_view)

        val editTitle = dialog_view.findViewById<EditText>(R.id.editTitle)
        editTitle.setSingleLine(true)
        editTitle.setGravity(Gravity.LEFT or Gravity.TOP)
        editTitle.setHorizontalScrollBarEnabled(false)

        dialog.setTitle("Create New Custom Album")

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", {
            _, _ ->

            val title = editTitle.getText().toString()
            val album = UserAlbumData(Title = title)

            AlbumAdapter.AlbumList.add(album)
            TheDataModel.addUserAlbum(album)
            runOnUiThread {
                AlbumAdapter.notifyDataSetChanged()
            }

        })

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", {
            _, _->

        })

        dialog.show()
    }



    fun gotoController(album: UserAlbumData) {


        var intent = Intent(this, TalkController::class.java)

        intent.putExtra("CONTENT", album.Content)
        intent.putExtra("TITLE", album.Title)
        startActivity(intent)
    }
}



class UserAlbumListAdapter(context: UserAlbumController) : BaseAdapter() {

    public var AlbumList = ArrayList<UserAlbumData>()
    var AlbumListView: ListView
    private val ControllerContext: UserAlbumController

    init {
        this.ControllerContext = context

        AlbumList = TheDataModel.getUserAlbums()

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

        val uri = "@drawable/albumdefault"
        imageResource = resources.getIdentifier(uri, null, ControllerContext.getPackageName())

        imageView.setImageDrawable(ContextCompat.getDrawable(ControllerContext, imageResource))


        return rowMain
    }

    fun albumSelected(position: Int) {

        var album: UserAlbumData = AlbumList[position]

        if (album.Title != "SECTIONHEADER") {

            ControllerContext.gotoController(album)
        }
    }

}




