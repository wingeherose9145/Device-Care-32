package com.system.helper

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    // 💡 数据库句柄：直接连接解压后的本地 SQLite
    private var database: SQLiteDatabase? = null
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 50音图标准矩阵配置（已完美修正换行和标点手误）
    private val hiraganaList = listOf(
        "あ", "い", "う", "え", "お", 
        "か", "き", "く", "け", "こ", 
        "さ", "し", "す", "せ", "そ", 
        "た", "ち", "つ", "て", "と", 
        "な", "に", "ぬ", "ね", "の", 
        "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "も", 
        "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", 
        "わ", "を", "ん", "假名", "促音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ",
        "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "斯", "单", "ソ",
        "タ", "チ", "ツ", "テ", "ト",
        "纳", "ニ", "努", "ネ", "ノ",
        "ハ", "飞", "フ", "ヘ", "ホ",
        "マ", "米", "姆", "メ", "モ",
        "ヤ", "ユ", "ヨ", "删除", "ー",
        "拉", "リ", "ル", "レ", "ロ",
        "ワ", "ヲ", "ン", "假名", "促音"
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
        
        searchBar.setOnClickListener {
            currentInput = ""
            matchAndFilter()
        }

        display.setOnLongClickListener {
            currentInput = ""
            matchAndFilter()
            true
        }

        // 🚀 后台异步：安全流式解压 assets 下的 dict.zip 并挂载数据库
        lifecycleScope.launch(Dispatchers.IO) {
            setupZipDatabase()
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    /**
     * ⚡ ZIP 自动化自动解压还原核心 ⚡
     * 自动从 assets/dict.zip 里抠出 dict.db 放进手机隔离存储区
     */
    private fun setupZipDatabase() {
        try {
            val dbFile = getDatabasePath("dict.db")
            
            // 首次安装运行时执行流式快速还原
            if (!dbFile.exists()) {
                val parent = dbFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                
                val assetInputStream = assets.open("dict.zip")
                val zipInputStream = ZipInputStream(assetInputStream)
                
                var zipEntry = zipInputStream.nextEntry
                while (zipEntry != null) {
                    if (zipEntry.name == "dict.db" || zipEntry.name.endsWith(".db")) {
                        val outputStream = FileOutputStream(dbFile)
                        val buffer = ByteArray(1024 * 64) // 64KB 高能传输缓存窗
                        var length: Int
                        while (zipInputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                        }
                        outputStream.flush()
                        outputStream.close()
                        break
                    }
                    zipInputStream.closeEntry()
                    zipEntry = zipInputStream.nextEntry
                }
                zipInputStream.close()
                assetInputStream.close()
            }
            
            // 解压出来后，以高性能、多线程、安全只读模式建立 SQLite 连接
            if (dbFile.exists()) {
                database = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanAllButtons(view: View) {
        if (view is MaterialButton) {
            buttonList.add(view)
        } else if (view is ViewGroup) {
            var i = 0
            while (i < view.childCount) {
                scanAllButtons(view.getChildAt(i))
                i++
            }
        }
    }

    private fun refreshButtonLabels() {
        val currentAlphabet = if (isHiragana) hiraganaList else katakanaList
        val maxIndex = minOf(buttonList.size, currentAlphabet.size)

        for (i in 0 until maxIndex) {
            val button = buttonList[i]
            val textValue = currentAlphabet[i]
            button.text = textValue
            button.setOnClickListener { handleButtonClick(textValue) }
        }
    }

    private fun setupSpecialLongClick() {
        for (button in buttonList) {
            if (button.id == R.id.btn_10_5) { 
                button.setOnLongClickListener {
                    if (unlocked) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    true
                }
            }
        }
    }

    private fun handleButtonClick(value: String) {
        when (value) {
            "删除" -> { 
                if (currentInput.isNotEmpty()) {
                    currentInput = currentInput.substring(0, currentInput.length - 1)
                    matchAndFilter()
                }
            }
            "ー" -> { 
                currentInput += "ー"
                matchAndFilter()
            }
            "假名" -> { 
                isHiragana = !isHiragana
                refreshButtonLabels()
            }
            "促音" -> { 
                if (currentInput.isNotEmpty()) {
                    val lastChar = currentInput.last().toString()
                    val converted = convertToTransformChar(lastChar)
                    currentInput = currentInput.substring(0, currentInput.length - 1) + converted
                    matchAndFilter()
                }
            }
            else -> {
                if (value.isNotEmpty()) {
                    currentInput += value
                    
                    inputSequence.add(value)
                    if (inputSequence.size > 5) inputSequence.removeAt(0)
                    if (inputSequence == secretSequence) unlocked = true

                    matchAndFilter()
                }
            }
        }
    }

    /**
     * ⚡ 高效率 SQL 前缀直查过滤器 ⚡
     */
    private fun matchAndFilter() {
        matchJob?.cancel()

        if (currentInput.isEmpty()) {
            filteredTexts = listOf()
            searchBar.text = ""
            display.text = ""
            return
        }

        searchBar.text = currentInput

        matchJob = lifecycleScope.launch {
            val input = currentInput
            
            val matchedList = withContext(Dispatchers.Default) {
                val results = mutableListOf<String>()
                val db = database
                
                if (db != null && db.isOpen) {
                    try {
                        // 🎯 对接单表直查架构：表名 mdx | 词头 word | 正文 text
                        val query = "SELECT word, text FROM mdx WHERE word LIKE ? LIMIT 20"
                        val cursor = db.rawQuery(query, arrayOf("$input%"))
                        
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0) ?: ""
                            val rawText = cursor.getString(1) ?: ""
                            
                            // 实时进行原生大段 Html 文本的高能排版渲染洗净
                            val cleanBody = if (rawText.isNotEmpty()) {
                                Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            } else {
                                "暂无释义"
                            }
                            
                            results.add("【$word】\n$cleanBody")
                        }
                        cursor.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                results
            }

            filteredTexts = matchedList
            updateDisplayResult()
        }
    }

    private fun convertToTransformChar(char: String): String {
        return when (char) {
            "つ" -> "っ"
            "っ" -> "つ"
            "や" -> "ゃ"
            "ゃ" -> "や"
            "ゆ" -> "ゅ"
            "ゅ" -> "ゆ"
            "よ" -> "ょ"
            "ょ" -> "よ"
            "あ" -> "ぁ"
            "ぁ" -> "あ"
            "い" -> "ぃ"
            "ぃ" -> "い"
            "う" -> "ぅ"
            "ぅ" -> "う"
            "え" -> "ぇ"
            "ぇ" -> "え"
            "お" -> "ぉ"
            "ぉ" -> "お"
            "ツ" -> "ッ"
            "ッ" -> "ツ"
            "ヤ" -> "ャ"
            "ャ" -> "ヤ"
            "ユ" -> "ュ"
            "ュ" -> "ユ"
            "ヨ" -> "ョ"
            "ョ" -> "ヨ"
            "ア" -> "ァ"
            "ァ" -> "ア"
            "イ" -> "ィ"
            "ィ" -> "い"
            "乌" -> "ゥ"
            "ウ" -> "ゥ"
            "ゥ" -> "乌"
            "工" -> "ェ"
            "エ" -> "ェ"
            "ェ" -> "工"
            "开" -> "テ"
            "オ" -> "ォ"
            "ォ" -> "开"
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
            "卡" -> "ガ"
            "カ" -> "ガ"
            "ガ" -> "卡"
            "キ" -> "ギ"
            "ギ" -> "キ"
            "ク" -> "グ"
            "グ" -> "ク"
            "ケ" -> "ゲ"
            "ゲ" -> "ケ"
            "コ" -> "ゴ"
            "ゴ" -> "ご"
            "ご" -> "コ"
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
            "サ" -> "ザ"
            "ザ" -> "サ"
            "シ" -> "ジ"
            "ジ" -> "修"
            "修" -> "シ"
            "斯" -> "ズ"
            "动" -> "ズ"
            "还原" -> "ズ"
            "ス" -> "ズ"
            "セ" -> "ゼ"
            "ゼ" -> "赛"
            "赛" -> "セ"
            "苏" -> "ゾ"
            "ソ" -> "ゾ"
            "ゾ" -> "苏"
            "た" -> "だ"
            "だ" -> "た"
            "ち" -> "ぢ"
            "ぢ" -> "ち"
            "て" -> "で"
            "で" -> "て"
            "と" -> "ど"
            "ど" -> "与"
            "与" -> "ど"
            "ど" -> "と"
            "タ" -> "ダ"
            "ダ" -> "タ"
            "チ" -> "ヂ"
            "ヂ" -> "チ"
            "テ" -> "デ"
            "デ" -> "テ"
            "制造" -> "ド"
            "ト" -> "ド"
            "导" -> "ト"
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
            "ハ" -> "バ"
            "バ" -> "パ"
            "パ" -> "ハ"
            "ヒ" -> "ビ"
            "ビ" -> "ピ"
            "ピ" -> "ヒ"
            "飞" -> "ビ"
            "フ" -> "ブ"
            "ブ" -> "プ"
            "プ" -> "フ"
            "ヘ" -> "ベ"
            "米" -> "ベ"
            "ベ" -> "ペ"
            "ペ" -> "ヘ"
            "ホ" -> "ボ"
            "ボ" -> "ポ"
            "ポ" -> "ホ"
            else -> char
        }
    }

    private fun updateDisplayResult() {
        if (currentInput.isEmpty()) {
            display.text = ""
            return
        }

        if (filteredTexts.isEmpty()) {
            display.text = ""
            return
        }

        val combinedText = filteredTexts.joinToString(separator = "\n\n")
        val spannable = SpannableString(combinedText)
        
        val goldColor = 0xFFFFD700.toInt()
        val highlightLength = currentInput.length

        if (highlightLength <= spannable.length) {
            spannable.setSpan(
                ForegroundColorSpan(goldColor),
                0,
                highlightLength,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        display.text = spannable
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (database?.isOpen == true) {
                database?.close()
            }
        } catch (e: Exception) { }
    }
}
