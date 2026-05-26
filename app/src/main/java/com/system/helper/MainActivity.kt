package com.system.helper

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var db: DictDbHelper
    private lateinit var listView: ListView
    private lateinit var searchBox: EditText

    private var results: List<DictItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        db = DictDbHelper(this)

        searchBox = findViewById(R.id.searchBox)
        listView = findViewById(R.id.listView)

        searchBox.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()

                results = if (text.isEmpty()) {
                    emptyList()
                } else {
                    db.search(text)
                }

                val words = results.map { it.word }

                listView.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    words
                )
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
