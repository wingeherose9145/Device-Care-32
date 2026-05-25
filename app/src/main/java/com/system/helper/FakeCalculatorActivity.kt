package com.system.helper

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
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
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    private var database: SQLiteDatabase? = null

    private val hiraganaList = listOf(
        "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", 
        "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", 
        "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "も", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "促音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "ミ", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
        "ラ", "リ", "ル", "レ", "ロ", "ワ", "ヲ", "ン", "假名", "促音"
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

        lifecycleScope.launch(Dispatchers.IO) { setupDatabase() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        display.setOnLongClickListener { currentInput = ""; matchAndFilter(); true }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    private fun setupDatabase() {
        try {
            val dbFile = getDatabasePath("dict.db")
            Log.d("DB", "数据库路径: ${dbFile.absolutePath}")

            if (!dbFile.exists()) {
                Log.d("DB", "开始解压 dict.zip...")
                assets.open("dict.zip").use { assetStream ->
                    ZipInputStream(assetStream).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
                            Log.d("DB", "ZIP Entry: ${entry.name}")
                            if (entry.name.endsWith(".db")) {
                                FileOutputStream(dbFile).use { output ->
                                    zipInput.copyTo(output)
                                }
                                Log.d("DB", "✅ 解压成功，大小: ${dbFile.length()} bytes")
                                break
                            }
                            entry = zipInput.nextEntry
                        }
                    }
                }
            } else {
                Log.d("DB", "数据库已存在，大小: ${dbFile.length()} bytes")
            }

            if (dbFile.exists()) {
                database = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                Log.d("DB", "✅ 数据库打开成功")
            }
        } catch (e: Exception) {
            Log.e("DB", "❌ 数据库初始化失败", e)
        }
    }

    private fun scanAllButtons(view: View) {
        if (view is MaterialButton) {
            buttonList.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanAllButtons(view.getChildAt(i))
            }
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
            "促音" -> if (currentInput.isNotEmpty()) {
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
            else -> char
        }
    }

    private fun matchAndFilter() {
        matchJob?.cancel()

        if (currentInput.isEmpty()) {
            filteredTexts = emptyList()
            searchBar.text = ""
            display.text = ""
            return
        }

        searchBar.text = currentInput
        Log.d("QUERY", "查询: '$currentInput'")

        matchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                database?.let { db ->
                    try {
                        val cursor = db.rawQuery("SELECT word, text FROM mdx WHERE word LIKE ? LIMIT 15", 
                            arrayOf("$currentInput%"))
                        var count = 0
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0) ?: ""
                            val rawText = cursor.getString(1) ?: ""
                            val clean = Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            list.add("【$word】\n$clean")
                            count++
                        }
                        cursor.close()
                        Log.d("QUERY", "找到 $count 条结果")
                    } catch (e: Exception) {
                        Log.e("QUERY", "查询失败", e)
                    }
                }
                list
            }
            filteredTexts = results
            updateDisplayResult()
        }
    }

    private fun updateDisplayResult() {
        if (filteredTexts.isEmpty()) {
            display.text = "未找到匹配词条\n(输入: $currentInput)"
            return
        }
        val combined = filteredTexts.joinToString("\n\n")
        val spannable = SpannableString(combined)
        val goldColor = 0xFFFFD700.toInt()

        if (currentInput.length <= combined.length) {
            spannable.setSpan(ForegroundColorSpan(goldColor), 0, currentInput.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        display.text = spannable
    }

    override fun onDestroy() {
        super.onDestroy()
        database?.close()
    }
}
