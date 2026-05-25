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
    
    private var currentInput = ""          
    private var matchJob: Job? = null

    private var database: SQLiteDatabase? = null
    private var isDbReady = false
    private var detectedTableName = "dictionary"
    private var detectedWordColumn = "word"
    private var detectedDefColumn = "definition"

    private val hiraganaList = listOf(
        "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", 
        "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", 
        "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "も", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "变音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "塞", "そ", "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "享", "ヘ", "ホ",
        "マ", "ミ", "ム", "美", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
        "ラ", "リ", "ル", "レ", "ロ", "哇", "ヲ", "ン", "假名", "变音"
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

        lifecycleScope.launch(Dispatchers.IO) { initDatabaseAndDumpSample() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        
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

    // ✨ 核心改变：加载数据库后，强行盲读前 3 条数据打印出来看看到底是什么鬼
    private fun initDatabaseAndDumpSample() {
        try {
            val dbFile = getDatabasePath("dict.db")
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                assets.open("dict.db").use { input ->
                    FileOutputStream(dbFile).use { output -> input.copyTo(output) }
                }
            }
            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            isDbReady = true
            
            // 🕵️‍♂️ 开始盲读取样
            val sampleList = mutableListOf<String>()
            try {
                val cursor = database!!.rawQuery("SELECT $detectedWordColumn, $detectedDefColumn FROM $detectedTableName LIMIT 3", null)
                while (cursor.moveToNext()) {
                    val w = cursor.getString(0) ?: "[空]"
                    val d = cursor.getString(1) ?: "[空]"
                    sampleList.add("单词列: $w\n释义列: ${if(d.length > 60) d.take(60) + "..." else d}")
                }
                cursor.close()
            } catch(e: Exception) {
                sampleList.add("盲读取样失败: ${e.message}")
            }

            runOnUiThread {
                val sampleText = sampleList.joinToString("\n---\n")
                display.text = "【数据库前3条样本直击】\n\n$sampleText\n\n====================\n请输入假名测试查询。"
            }
        } catch (e: Exception) {
            Log.e("SQL_DB", "数据库加载异常", e)
            runOnUiThread { display.text = "加载异常: ${e.message}" }
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
            "て" -> "で"
            "to" -> "ど"
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

    private fun matchAndFilter() {
        matchJob?.cancel()

        if (currentInput.isEmpty()) {
            searchBar.text = ""
            matchJob = lifecycleScope.launch { initDatabaseAndDumpSample() }
            return
        }

        searchBar.text = currentInput

        matchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                
                if (isDbReady && database != null) {
                    try {
                        val querySQL = "SELECT $detectedWordColumn, $detectedDefColumn FROM $detectedTableName WHERE $detectedWordColumn LIKE ? OR $detectedDefColumn LIKE ? LIMIT 15"
                        val keyword = "%$currentInput%"
                        val cursor = database!!.rawQuery(querySQL, arrayOf(keyword, keyword))
                        
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0) ?: ""
                            val definition = cursor.getString(1) ?: ""
                            
                            val cleanDef = Html.fromHtml(definition, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            list.add("【$word】\n$cleanDef")
                        }
                        cursor.close()
                    } catch (e: Exception) {
                        Log.e("SQL_QUERY", "检索执行失败", e)
                    }
                }
                list
            }
            
            if (results.isEmpty()) {
                display.text = "未找到匹配词条\n(输入: $currentInput)"
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
