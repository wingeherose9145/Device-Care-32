package com.system.helper

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Html
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
        "な", "に", "ぬ", "ね", "之", "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "も", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "变音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "そ", "タ", "チ", "ツ", "テ", "ト",
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

        lifecycleScope.launch(Dispatchers.IO) { initDatabase() }

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

    private fun initDatabase() {
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
            
            runOnUiThread {
                display.text = "✅ 词库完全就绪！\n请输入假名开始查词。"
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
            "я" -> "ゃ"
            "ゃ" -> "や"
            "ゆ" -> "ゅ"
            "ゅ" -> "ゆ"
            "よ" -> "ょ"
            "ょ" -> "よ"
            else -> char
        }
    }

    // 针对原始词典自定义 HTML 标签的清洗与排版规范化预处理器
    private fun preprocessHtml(word: String, rawHtml: String): String {
        var html = rawHtml

        // 1. 过滤不兼容的外部 CSS 外链样式
        html = html.replace(Regex("<link[^>]*>"), "")

        // 2. 规范化词头 <h3>：转为标准加粗并强制分离换行
        html = html.replace("<h3>", "<br/><b>")
        html = html.replace("</h3>", "</b><br/>")

        // 3. 规范化日语例句标签 <jae>：行首缩进，加粗强调
        html = html.replace("<jae>", "<br/>&nbsp;&nbsp;•&nbsp;<b>")
        html = html.replace("</jae>", "</b>")

        // 4. 规范化中文解释标签 <ja_cn>：行首缩进，使用弱灰色区分，作为例句的附属属性
        html = html.replace("<ja_cn>", "<br/>&nbsp;&nbsp;&nbsp;&nbsp;<font color='#808080'>")
        html = html.replace("</ja_cn>", "</font>")

        // 5. 转换语义块标签 <span type="...">：通过【】实体符号建立清晰的内容边界
        html = html.replace(Regex("<span[^>]*>"), "【")
        html = html.replace("</span>", "】")

        // 返回包含主词头和清洗排版后 HTML 的复合字符串
        return "<b>【$word】</b>$html"
    }

    // 利用 SQLite 的 SUBSTR() 函数进行轻量级切片模糊匹配，安全跨越 2MB 限制并完美展现格式层级
    private fun matchAndFilter() {
        matchJob?.cancel()

        if (currentInput.isEmpty()) {
            searchBar.text = ""
            display.text = "请在下方输入假名进行查词"
            return
        }

        searchBar.text = currentInput

        matchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                
                if (isDbReady && database != null) {
                    try {
                        // 🟢 核心切片逻辑：利用 SUBSTR 只截取前 150 个字符拉入内存匹配，保障单行体积不崩溃
                        val querySQL = "SELECT $detectedWordColumn FROM $detectedTableName WHERE $detectedWordColumn LIKE ? OR SUBSTR($detectedDefColumn, 1, 150) LIKE ? LIMIT 15"
                        val keyword = "%$currentInput%"
                        val cursor = database!!.rawQuery(querySQL, arrayOf(keyword, keyword))
                        
                        val matchedWords = mutableListOf<String>()
                        while (cursor.moveToNext()) {
                            val w = cursor.getString(0) ?: ""
                            if (w.isNotEmpty()) matchedWords.add(w)
                        }
                        cursor.close()

                        // 🟢 分步精准拉取详细释义的原始 XML/HTML 串
                        for (wordItem in matchedWords) {
                            try {
                                val singleCursor = database!!.rawQuery(
                                    "SELECT $detectedDefColumn FROM $detectedTableName WHERE $detectedWordColumn = ? LIMIT 1",
                                    arrayOf(wordItem)
                                )
                                if (singleCursor.moveToFirst()) {
                                    val definition = singleCursor.getString(0) ?: ""
                                    
                                    // 🟢 调用 HTML 标签规范器清洗词条
                                    val formattedHtml = preprocessHtml(wordItem, definition)
                                    
                                    // 限制单条最长显示容量，避免特长解释渲染卡顿
                                    val safeHtml = if (formattedHtml.length > 3000) {
                                        formattedHtml.take(3000) + "<br/><font color='red'>...(内容过多，已截断显示)</font>"
                                    } else {
                                        formattedHtml
                                    }
                                    list.add(safeHtml)
                                }
                                singleCursor.close()
                            } catch (e: Exception) {
                                list.add("<b>【$wordItem】</b><br/><font color='red'>[⚠️ 该词条单行过大，Android 系统限制载入]</font>")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SQL_QUERY", "切片联合查询执行失败", e)
                    }
                }
                list
            }
            
            // UI 更新与系统级 HTML 渲染映射
            if (results.isEmpty()) {
                display.text = "未找到匹配词条\n(已检索汉字列及释义注音区)"
            } else {
                // 1. 各词条之间使用标准细线标签 <hr/> 进行解耦隔离
                val combinedHtml = results.joinToString("<br/><br/><hr/><br/>")
                
                // 2. ⚠️ 核心修正：利用 Android 原生机制解析为 Spanned，完美传承
