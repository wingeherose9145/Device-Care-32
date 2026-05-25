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
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    // ✨ 修复：重新补上漏掉的搜索控制变量
    private var currentInput = ""          
    private var matchJob: Job? = null

    // 数据库物理连接与侦测缓存
    private var database: SQLiteDatabase? = null
    private var isDbReady = false
    private var detectedTableName: String = ""
    private var detectedWordColumn: String = ""
    private var detectedDefColumn: String = ""

    // 标准日文假名键盘映射
    private val hiraganaList = listOf(
        "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", 
        "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", 
        "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "mo", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "变音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "ミ", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
        "ラ", "リ", "ル", "レ", "ロ", "ワ", "ヲ", "ン", "假名", "变音"
    )

    private var isHiragana = true
    private val buttonList = mutableListOf<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN 
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        setContentView(R.layout.activity_fake_calculator)
        display = findViewById(R.id.display)
        searchBar = findViewById(R.id.search_bar) 

        // 异步安全初始化本地二进制数据库并侦测结构
        lifecycleScope.launch(Dispatchers.IO) { initAndInspectDatabase() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        
        // 长按复制结果
        display.setOnLongClickListener { 
            if (display.text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Dict Result", display.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "词条内容已复制", Toast.LENGTH_SHORT).show()
            }
            true 
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    // 🕵️‍♂️ 核心侦测逻辑：把任意未知数据库的结构翻个底朝天并可视化显示
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
            
            // 1. 获取所有的真实表名
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
                // 默认探测第一张有效的词典用户表
                detectedTableName = tableNames[0]
                
                // 2. 获取这张表里所有的列名（字段名）
                val columnCursor = database!!.rawQuery("PRAGMA table_info($detectedTableName)", null)
                val columnNames = mutableListOf<String>()
                while (columnCursor.moveToNext()) {
                    columnNames.add(columnCursor.getString(1))
                }
                columnCursor.close()
                Log.e("DB_INSPECT", "表【$detectedTableName】中包含的列名(字段): $columnNames")
                
                // 3. 智能猜测模糊列名并锁定目标
                detectedWordColumn = columnNames.find { 
                    it.contains("word") || it.contains("kanji") || it.contains("kana") || 
                    it.contains("key") || it.contains("heading") || it.contains("title") 
                } ?: columnNames[0]
                
                detectedDefColumn = columnNames.find { 
                    it.contains("def") || it.contains("text") || it.contains("content") || 
                    it.contains("value") || it.contains("result") 
                } ?: if(columnNames.size > 1) columnNames[1] else columnNames[0]
                
                Log.e("DB_INSPECT", "💡 智能匹配成功 -> 锁定表【$detectedTableName】的【$detectedWordColumn】与【$detectedDefColumn】")
                isDbReady = true
            } else {
                Log.e("DB_INSPECT", "❌ 错误：这个数据库里没有任何有效的表格！")
            }
            Log.e("DB_INSPECT", "====================== 数据库结构侦测结束 ======================")
            
            // 侦测完成后，把数据库内部具体的字段名直接抛到手机屏幕上
            withContext(Dispatchers.Main) {
                if (isDbReady) {
                    display.text = "数据库连接成功！\n表名: $detectedTableName\n单词字段: $detectedWordColumn\n释义字段: $detectedDefColumn\n\n请现在在下方输入假名进行查词！"
                } else {
                    display.text = "数据库连接失败或表格为空！\n请检查资产目录下是否存在非空 dict.db"
                }
            }

        } catch (e: Exception) {
            Log.e("DB_INSPECT", "❌ 数据库读取发生严重错误", e)
            try {
                withContext(Dispatchers.Main) { display.text = "数据库加载异常: ${e.message}" }
            } catch(_: Exception){}
        }
    }

    private fun scanAllButtons(view: View) {
        if (view is MaterialButton) buttonList.add(view)
        else if (view is ViewGroup) {
            for (i in 0 until view.childCount) scanAllButtons(view.getChildAt(i))
        }
    }

    private fun refreshButtonLabels() {
        val currentList = if (isHiragana) hiraganaList else katakanaList
        for (i in 0 until minOf(buttonList.size, currentList.size)) {
            val button = buttonList[i]
            button.text = currentList[i]
            button.setOnClickListener { handleButtonClick(currentList[i]) }
        }
    }

    private fun setupSpecialLongClick() {
        buttonList.find { it.id == R.id.btn_10_5 }?.setOnLongClickListener {
            if (unlocked) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            true
        }
    }

    private fun handleButtonClick(value: String) {
        when (value) {
            "删除" -> if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            "ー" -> currentInput += "ー"
            "假名" -> {
                isHiragana = !isHiragana
                refreshButtonLabels()
                return
            }
            "变音" -> if (currentInput.isNotEmpty()) {
                val last = currentInput.last().toString()
                currentInput = currentInput.dropLast(1) + convertToTransformChar(last)
            }
            else -> {
                currentInput += value
                inputSequence.add(value)
                if (inputSequence.size > 5) inputSequence.removeAt(0)
                if (inputSequence == secretSequence) unlocked = true
            }
        }
        matchAndFilter()
    }

    private fun convertToTransformChar(char: String): String {
        return when (char) {
            "つ" -> "っ"
            "っ" -> "づ"
            "づ" -> "つ"
            "か" -> "が"
            "が" -> "か"
            "き" -> "ぎ"
            "ぎ" -> "き"
            "く" -> "ぐ"
            "ぐ" -> "く"
            "け" -> "げ"
            "げ" -> "け"
            "こ" -> "ご"
            "ご" -> "こ"
            "さ" -> "ざ"
            "ざ" -> "さ"
            "し" -> "じ"
            "じ" -> "し"
            "す" -> "ず"
            "ず" -> "す"
            "せ" -> "ぜ"
            "ぜ" -> "せ"
            "そ" -> "ぞ"
            "ぞ" -> "そ"
            "た" -> "だ"
            "だ" -> "た"
            "ち" -> "ぢ"
            "ぢ" -> "ち"
            "て" -> "で"
            "电" -> "て"
            "と" -> "ど"
            "ど" -> "と"
            "は" -> "ば"
            "ば" -> "ぱ"
            "ぱ" -> "は"
            "ひ" -> "び"
            "び" -> "ぴ"
            "ぴ" -> "ひ"
            "ふ" -> "ぶ"
            "ぶ" -> "ぷ"
            "ぷ" -> "ふ"
            "へ" -> "べ"
            "べ" -> "ぺ"
            "ぺ" -> "へ"
            "ほ" -> "ぼ"
            "ぼ" -> "ぽ"
            "ぽ" -> "ほ"
            "や" -> "ゃ"
            "ゃ" -> "や"
            "ゆ" -> "ゅ"
            "ゅ" -> "ゆ"
            "よ" -> "ょ"
            "ょ" -> "よ"
            else -> char
        }
    }

    // 动态嗅探模糊查询：用手机识别出来的真实列名去调取系统底层 SQLite
    private fun matchAndFilter() {
        matchJob?.cancel()

        if (currentInput.isEmpty()) {
            searchBar.text = ""
            display.text = if (isDbReady) {
                "表名: $detectedTableName\n单词字段: $detectedWordColumn\n释义字段: $detectedDefColumn\n\n请在下方输入假名进行查词！"
            } else {
                "数据库未就绪"
            }
            return
        }

        searchBar.text = currentInput

        matchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                
                if (isDbReady && database != null && detectedTableName.isNotEmpty()) {
                    try {
                        // ✨ 终极动态 SQL 检索语句：完全规避字段名不一致的短板
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
                        Log.e("SQL_QUERY", "动态执行检索错误", e)
                    }
                }
                list
            }
            
            // 更新 UI 显示结果并进行上色
            if (results.isEmpty()) {
                display.text = "未找到匹配词条\n(目标表: $detectedTableName, 检索字段: $detectedWordColumn)"
            } else {
                val combined = results.joinToString("\n\n")
                val spannable = SpannableString(combined)
                
                val goldColor = 0xFFFFD700.toInt()
                val itemBgColor = 0x1AFFFFFF.toInt() 

                if (currentInput.length <= combined.length) {
                    spannable.setSpan(ForegroundColorSpan(goldColor), 0, currentInput.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                
                var currentIndex = 0
                for (text in results) {
                    val start = currentIndex
                    val end = currentIndex + text.length
                    if (end <= spannable.length) {
                        spannable.setSpan(BackgroundColorSpan(itemBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    currentIndex = end + 2 
                }
                display.text = spannable
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        database?.close()
    }
}
