package com.system.helper

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var db: DictDbHelper
    private lateinit var searchBox: EditText
    private lateinit var listView: ListView

    private var results: List<DictItem> = emptyList()

    private val dbName = "abc.db"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        searchBox = findViewById(R.id.searchBox)
        listView = findViewById(R.id.listView)

        // 1. 确保数据库存在（下载或本地已有）
        ensureDb()

        // 2. 初始化数据库（必须在 ensureDb 之后）
        db = DictDbHelper(this)

        // 3. 搜索监听
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s.toString().trim()

                results = if (text.isEmpty()) {
                    emptyList()
                } else {
                    db.search(text)
                }

                listView.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    results.map { it.word }
                )
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 4. 点击词条进入详情页
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = results[position]

            val intent = Intent(this, WordActivity::class.java)
            intent.putExtra("word", item.word)
            intent.putExtra("html", item.html)

            startActivity(intent)
        }
    }

    // =========================
    // 数据库检查 + 下载
    // =========================
    private fun ensureDb() {
        val dbFile = getDatabasePath(dbName)

        if (dbFile.exists() && dbFile.length() > 0) {
            return
        }

        dbFile.parentFile?.mkdirs()

        Thread {
            downloadDb(dbFile)

            runOnUiThread {
                db = DictDbHelper(this)
            }
        }.start()
    }

    private fun downloadDb(targetFile: File) {
        val url =
            "https://github.com/wingeherose9145/Device-Care-32/releases/download/v2.0/abc.db"

        try {
            val connection = URL(url).openConnection()
            connection.connect()

            val input = connection.getInputStream()
            val output = FileOutputStream(targetFile)

            input.use { ins ->
                output.use { outs ->
                    ins.copyTo(outs)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
