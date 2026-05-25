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
        "ま", "み", "む", "め", "做", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "促音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "卡", "キ", "ク", "ケ", "コ",
        "サ", "シ", "斯", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "ミ", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
        "拉", "リ", "ル", "レ", "ロ", "哇", "ヲ", "ン", "假名", "促音"
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
            if (!dbFile.exists()) {
                Log.d("DB", "正在解压 dict.zip...")
                
                // ✨ 修复 1：强制确保父级目录（databases 文件夹）存在，防止写入时抛出 FileNotFoundException
                dbFile.parentFile?.mkdirs()

                assets.open("dict.zip").use { assetStream ->
                    ZipInputStream(assetStream).use { zipInput ->
                        var entry = zipInput.nextEntry
                        while (entry != null) {
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
            }

            if (dbFile.exists()) {
                database = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                Log.d("DB", "✅ 数据库加载成功")
                
                // ✨ 调试日志：检查数据库解压后里面到底有几条数据
                database?.let { db ->
                    try {
                        val testCursor = db.rawQuery("SELECT COUNT(*) FROM dictionary", null)
                        if (testCursor.moveToFirst()) {
                            Log.d("DB_CHECK", "【重要数据】当前 dictionary 表内共有数据: ${testCursor.getInt(0)} 条")
                        }
                        testCursor.close()
                    } catch (e: Exception) {
                        Log.e("DB_CHECK", "❌ 无法查询 COUNT(*)，表名 dictionary 可能不存在或字段对齐损坏", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DB", "❌ 数据库加载失败", e)
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

        matchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val list = mutableListOf<String>()
                database?.let { db ->
                    try {
                        // ✨ 修复 2：将模糊通配符修改为标准 SQLite 的 `LIKE ? || '%'` 拼接，确保对日语假名的前缀匹配支持
                        val cursor = db.rawQuery(
                            "SELECT word, definition FROM dictionary WHERE word LIKE ? || '%' LIMIT 15", 
                            arrayOf(currentInput)
                        )
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0) ?: ""
                            val rawText = cursor.getString(1) ?: ""
                            val clean = Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            list.add("【$word】\n$clean")
                        }
                        cursor.close()
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
