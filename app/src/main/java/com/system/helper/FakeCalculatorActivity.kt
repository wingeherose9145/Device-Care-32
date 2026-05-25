package com.system.helper

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private var database: SQLiteDatabase? = null
    private var isDbReady = false

    // 临时增加：保存侦测到的真实表名和列名
    private var detectedTableName: String = ""
    private var detectedWordColumn: String = ""
    private var detectedDefColumn: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_calculator)
        display = findViewById(R.id.display)
        searchBar = findViewById(R.id.search_bar) 

        // 异步安全初始化本地二进制数据库并侦测结构
        lifecycleScope.launch(Dispatchers.IO) { initAndInspectDatabase() }
    }

    // 🕵️‍♂️ 核心侦测逻辑：把数据库的底细翻个底朝天
    private fun initAndInspectDatabase() {
        try {
            val dbFile = getDatabasePath("dict.db")
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                assets.open("dict.db").use { input ->
                    FileOutputStream(dbFile).use { output -> input.copyTo(output) }
                }
            }
            
            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            
            Log.e("DB_INSPECT", "====================== 数据库结构侦测开始 ======================")
            
            // 1. 获取所有的表名
            val tableCursor = database!!.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            val tableNames = mutableListOf<String>()
            while (tableCursor.moveToNext()) {
                val tName = tableCursor.getString(0)
                if (tName != "android_metadata" && tName != "sqlite_sequence") {
                    tableNames.add(tName)
                }
            }
            tableCursor.close()
            Log.e("DB_INSPECT", "发现数据库中包含的表名: $tableNames")

            if (tableNames.isNotEmpty()) {
                // 取第一个有效的用户表
                detectedTableName = tableNames[0]
                
                // 2. 获取这张表里所有的列名（字段名）
                val columnCursor = database!!.rawQuery("PRAGMA table_info($detectedTableName)", null)
                val columnNames = mutableListOf<String>()
                while (columnCursor.moveToNext()) {
                    // name 在第 1 列
                    columnNames.add(columnCursor.getString(1))
                }
                columnCursor.close()
                Log.e("DB_INSPECT", "表【$detectedTableName】中包含的列名(字段): $columnNames")
                
                // 3. 智能猜测列名并锁死
                // 单词列通常包含 word, kanji, kana, key, heading, title 中的一个
                detectedWordColumn = columnNames.find { it.contains("word") || it.contains("kanji") || it.contains("kana") || it.contains("key") || it.contains("heading") || it.contains("title") } ?: columnNames[0]
                // 释义列通常包含 def, text, content, value, result 中的一个
                detectedDefColumn = columnNames.find { it.contains("def") || it.contains("text") || it.contains("content") || it.contains("value") || it.contains("result") } ?: if(columnNames.size > 1) columnNames[1] else columnNames[0]
                
                Log.e("DB_INSPECT", "💡 智能匹配成功 -> 将使用表【$detectedTableName】的【$detectedWordColumn】作为单词，【$detectedDefColumn】作为释义去查询！")
                isDbReady = true
            } else {
                Log.e("DB_INSPECT", "❌ 错误：这个数据库里没有任何有效的表格！")
            }
            Log.e("DB_INSPECT", "====================== 数据库结构侦测结束 ======================")
            
            // 侦测完成后，把数据库结构直接显示在手机屏幕上给你看
            withContext(Dispatchers.Main) {
                if (isDbReady) {
                    display.text = "数据库连接成功！\n表名: $detectedTableName\n单词字段: $detectedWordColumn\n释义字段: $detectedDefColumn\n\n请现在尝试输入查词！"
                } else {
                    display.text = "数据库连接失败或表格为空！"
                }
            }

        } catch (e: Exception) {
            Log.e("DB_INSPECT", "❌ 数据库读取发生严重错误", e)
        }
    }

    // 查词时，动态使用刚刚侦测出来的真实字段名去查
    private fun matchAndFilter() {
        if (currentInput.isEmpty() || !isDbReady || database == null) return
        searchBar.text = currentInput

        lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                try {
                    // ✨ 终极动态 SQL：用手机亲自嗅探出来的表名和字段名去查，绝对不会因为名字对不上而翻车！
                    val querySQL = "SELECT $detectedWordColumn, $detectedDefColumn FROM $detectedTableName WHERE $detectedWordColumn LIKE ? LIMIT 15"
                    val cursor = database!!.rawQuery(querySQL, arrayOf("%$currentInput%"))
                    
                    while (cursor.moveToNext()) {
                        val word = cursor.getString(0) ?: ""
                        val definition = cursor.getString(1) ?: ""
                        val cleanDef = Html.fromHtml(definition, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        list.add("【$word】\n$cleanDef")
                    }
                    cursor.close()
                } catch (e: Exception) {
                    Log.e("SQL_QUERY", "动态查询失败", e)
                }
                list
            }
            
            if (results.isEmpty()) {
                display.text = "未找到匹配词条\n(使用动态SQL: 查表 $detectedTableName)"
            } else {
                display.text = results.joinToString("\n\n")
            }
        }
    }
}
