package com.system.helper

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DictDbHelper

    private lateinit var searchBox: EditText
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var results = listOf<DictItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        searchBox = findViewById(R.id.searchBox)
        listView = findViewById(R.id.listView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        dbHelper = DictDbHelper(this)

        checkDatabase()
    }

    /**
     * 检测数据库
     */
    private fun checkDatabase() {

        if (dbHelper.isDatabaseExists()) {

            initSearch()

        } else {

            downloadDatabase()

        }
    }

    /**
     * 下载数据库
     */
    private fun downloadDatabase() {

        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE

        statusText.text = "正在下载词库..."

        lifecycleScope.launch {

            val success = Downloader.downloadDatabase(this@MainActivity)

            progressBar.visibility = View.GONE

            if (success) {

                statusText.text = "词库下载完成"

                dbHelper = DictDbHelper(this@MainActivity)

                initSearch()

            } else {

                statusText.text = "词库下载失败"
            }
        }
    }

    /**
     * 初始化搜索
     */
    private fun initSearch() {

        statusText.visibility = View.GONE

        searchBox.isEnabled = true

        searchBox.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                val text = s.toString()

                results = dbHelper.search(text)

                val words = results.map {

                    if (it.reading.isNotBlank()) {
                        "${it.word} 【${it.reading}】"
                    } else {
                        it.word
                    }
                }

                listView.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    words
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->

            val item = results[position]

            val intent = Intent(this, WordActivity::class.java)

            intent.putExtra("word", item.word)
            intent.putExtra("html", item.html)

            startActivity(intent)
        }
    }
}
