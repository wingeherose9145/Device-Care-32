package com.system.helper

import android.app.AlertDialog
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

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    private val videoUris = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()

    private lateinit var adapter: ArrayAdapter<String>

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->

        if (uris.isNullOrEmpty()) return@registerForActivityResult

        uris.forEach { uri ->

            try {
                // 防止重复添加
                if (videoUris.contains(uri)) return@forEach

                // 持久化权限（防止部分设备无法读取）
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // 某些系统会抛异常，忽略即可
                }

                videoUris.add(uri)
                displayNames.add(getFileNameFromUri(uri))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.videoListView)
        val addButton = findViewById<Button>(R.id.addButton)

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            displayNames
        )

        listView.adapter = adapter

        // 添加视频
        addButton.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }

        // 点击播放
        listView.setOnItemClickListener { _, _, position, _ ->

            val intent = Intent(this, PlayerActivity::class.java)

            intent.putExtra(
                "video_uri",
                videoUris[position].toString()
            )

            intent.putExtra(
                "current_index",
                position
            )

            intent.putStringArrayListExtra(
                "video_list",
                ArrayList(videoUris.map { it.toString() })
            )

            startActivity(intent)
        }

        // 长按删除（已修复 Kotlin 类型推断问题）
        listView.setOnItemLongClickListener { _, _, position: Int, _ ->

            AlertDialog.Builder(this@MainActivity)
                .setTitle("删除视频")
                .setMessage("确定删除该视频？")
                .setPositiveButton("删除") { _, _ ->

                    if (position >= 0 && position < videoUris.size) {

                        videoUris.removeAt(position)
                        displayNames.removeAt(position)

                        adapter.notifyDataSetChanged()

                        Toast.makeText(
                            this@MainActivity,
                            "已删除",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

            true
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {

        return try {

            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->

                val nameIndex =
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (cursor.moveToFirst() && nameIndex != -1) {
                    cursor.getString(nameIndex)
                } else {
                    "未知视频"
                }
            } ?: "未知视频"

        } catch (e: Exception) {
            "未知视频"
        }
    }
}
