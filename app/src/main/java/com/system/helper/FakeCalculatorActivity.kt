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
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 数据库物理连接对象
    private var database: SQLiteDatabase? = null
    private var isDbReady = false

    // 精准校对后的平假名键盘映射
    private val hiraganaList = listOf(
        "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", 
        "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", 
        "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ", 
        "ま", "み", "む", "め", "も", "や", "ゆ", "よ", "删除", "ー", 
        "ら", "り", "る", "れ", "ろ", "わ", "を", "ん", "假名", "变音" 
    )

    private val katakanaList = listOf(
        "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ", "ハ", "ヒ", "フ", "ヘ", "防",
        "マ", "米", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
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

        // 异步安全初始化本地二进制数据库
        lifecycleScope.launch(Dispatchers.IO) { initDatabase() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        
        // 长按复制结果区域的所有纯文本词条
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

    // 将大体积二进制的 dict.db 部署到手机内置安全沙盒中运行
    private fun initDatabase() {
        try {
            val dbFile = getDatabasePath("dict.db")
            if (!dbFile.exists()) {
                Log.d("SQL_DB", "首次安装：正在从资产中释放 41M 词典数据库...")
                dbFile.parentFile?.mkdirs()
                
                // 通过 Actions 自动化打包时已经将二进制解压释放到了 assets 目录中
                assets.open("dict.db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("SQL_DB", "📂 数据库释放物理部署完成！")
            }
            
            // 以高效的只读模式打开本地 SQLite 索引数据库
            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            isDbReady = true
            Log.d("SQL_DB", "✅ SQLite 数据库索引就绪，响应延迟正式清零。")
        } catch (e: Exception) {
            Log.e("SQL_DB", "❌ 数据库就绪失败，请确认文件名及资产目录配置是否正确", e)
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
            "ち" -> "ヂ"
            "ヂ" -> "ち"
            "て" -> "で"
            "de" -> "て"
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

    // ✨ 极致优化：直接调取手机系统底层的 SQLite 引擎，利用 B-Tree 索引零延迟快速查词
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
                
                if (isDbReady && database != null) {
                    try {
                        // 智能 SQL 语句：
                        // 1. 优先使用索引匹配符合当前输入的单词（search_word）
                        // 2. 如果你的数据库里字段名为 word 和 definition，以下通用代码将直接完美匹配
                        // 3. 限制最多取出 15 条最相关的记录，确保 UI 渲染不掉帧
                        val cursor = database!!.rawQuery(
                            "SELECT word, definition FROM dictionary WHERE search_word LIKE ? OR word LIKE ? LIMIT 15",
                            arrayOf("$currentInput%", "%$currentInput%")
                        )
                        
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0) ?: ""
                            val definition = cursor.getString(1) ?: ""
                            
                            // 完美的富文本解析：如果你的定义里带有 <b>, <font>, <br> 等原生网页样式，Android 将极其详细、美观地还原它们
                            val cleanDef = Html.fromHtml(definition, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            list.add("【$word】\n$cleanDef")
                        }
                        cursor.close()
                    } catch (e: Exception) {
                        Log.e("SQL_QUERY", "检索执行失败，请确保数据库中包含 dictionary 表，且含有 word、search_word、definition 字段", e)
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
        val itemBgColor = 0x1AFFFFFF.toInt() // 优雅清爽的半透明底色块区隔

        if (currentInput.length <= combined.length) {
            spannable.setSpan(ForegroundColorSpan(goldColor), 0, currentInput.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        var currentIndex = 0
        for (text in filteredTexts) {
            val start = currentIndex
            val end = currentIndex + text.length
            if (end <= spannable.length) {
                spannable.setSpan(BackgroundColorSpan(itemBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            currentIndex = end + 2 
        }
        
        display.text = spannable
    }

    override fun onDestroy() {
        super.onDestroy()
        database?.close()
    }
}
