package com.system.helper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val videoUris = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    // 1. 选择视频的回调（保留第一版的选择方式）
    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        uris.forEach { uri ->
            if (!videoUris.contains(uri)) {
                try {
                    // 【关键点】申请永久访问该文件的权限，否则重启APP后无法读取
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    videoUris.add(uri)
                    displayNames.add(getFileNameFromUri(uri))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        adapter.notifyDataSetChanged()
        saveList() // 每次添加后保存
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.videoListView)
        val addButton = findViewById<Button>(R.id.addButton)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        // 加载上次保存的内容
        loadSavedList()

        // 按钮逻辑：添加新视频
        addButton.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }

        // 列表点击：正常播放
        listView.setOnItemClickListener { _, _, position, _ ->
            playVideo(position, false)
        }

        // 【新功能】如果列表不为空，进入APP后自动随机播放
        if (videoUris.isNotEmpty()) {
            val randomIndex = videoUris.indices.random()
            playVideo(randomIndex, true) 
        }
    }

    private fun playVideo(startIndex: Int, isRandom: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("video_uri", videoUris[startIndex].toString())
        intent.putExtra("current_index", startIndex)
        
        // 传递整个列表
        val listStrings = ArrayList(videoUris.map { it.toString() })
        intent.putStringArrayListExtra("video_list", listStrings)
        
        // 如果需要随机模式，可以传个标记位给 PlayerActivity
        intent.putExtra("is_random_mode", isRandom)
        
        startActivity(intent)
    }

    // 保存列表到本地（使用第一问中你提到的 Gson）
    private fun saveList() {
        val prefs = getSharedPreferences("app_data", MODE_PRIVATE)
        val uriStrings = videoUris.map { it.toString() }
        prefs.edit().putString("saved_uris", Gson().toJson(uriStrings)).apply()
        prefs.edit().putString("saved_names", Gson().toJson(displayNames)).apply()
    }

    // 读取本地保存的列表
    private fun loadSavedList() {
        val prefs = getSharedPreferences("app_data", MODE_PRIVATE)
        val uriJson = prefs.getString("saved_uris", null) ?: return
        val nameJson = prefs.getString("saved_names", null) ?: return

        val type = object : TypeToken<List<String>>() {}.type
        val savedUris: List<String> = Gson().fromJson(uriJson, type)
        val savedNames: List<String> = Gson().fromJson(nameJson, type)

        videoUris.clear()
        displayNames.clear()
        
        savedUris.forEach { 
            val uri = Uri.parse(it)
            // 检查是否依然拥有该 Uri 的权限
            videoUris.add(uri) 
        }
        displayNames.addAll(savedNames)
        adapter.notifyDataSetChanged()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) cursor.getString(nameIndex) else "未知视频"
        } ?: "未知视频"
    }
}
