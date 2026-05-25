package com.system.helper

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import java.io.BufferedReader
import java.io.InputStreamReader

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 优化的内存字典结构，用于极速索引
    // ArrayEntry 存储: 0=原词, 1=过滤了特殊符号的纯单词, 2=原释义, 3=过滤了排版符的纯释义
    private val dictionaryList = mutableListOf<Array<String>>()
    private var isDictionaryLoaded = false

    // ✨ 修复 2：全面校对并修正了平假名中的错别字与元音顺序
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

        lifecycleScope.launch(Dispatchers.IO) { loadTxtDatabase() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        
        // ✨ 修复 5：长按结果显示区域，一键复制当前查出来的所有词条
        display.setOnLongClickListener { 
            if (display.text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Dictionary Result", display.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "词条内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            true 
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    // ✨ 修复 1 & 6：大幅优化大文本加载，预先清洗检索文本，降低匹配时的计算开销
    private fun loadTxtDatabase() {
        try {
            Log.d("TXT_DB", "开始高性能流式装载...")
            val startTime = System.currentTimeMillis()
            
            assets.open("dict.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8"), 65536).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty()) continue
                        
                        val parts = trimmed.split("|||")
                        if (parts.size >= 2) {
                            val originalWord = parts[0].trim()
                            val originalDef = parts[1].trim()
                            
                            // 预生成专供检索用的纯净文本（剥离【】、\n、・等控制符，防止误伤匹配）
                            val searchWord = originalWord.replace(Regex("[【】\\[\\]\\s]"), "")
                            val searchDef = originalDef.replace("\\n", "").replace(Regex("[【】\\[\\]・\\s]"), "")
                            
                            dictionaryList.add(arrayOf(originalWord, searchWord, originalDef, searchDef))
                        }
                    }
                    isDictionaryLoaded = true
                    Log.d("TXT_DB", "✅ 词库加载成功，耗时: ${System.currentTimeMillis() - startTime}ms")
                }
            }
        } catch (e: Exception) {
            Log.e("TXT_DB", "❌ 词库加载失败", e)
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

    // ✨ 修复 3：全面重构清爽的促音、浊音、半浊音循环切换机制（变音键）
    private fun convertToTransformChar(char: String): String {
        return when (char) {
            // 平假名
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
            "で" -> "て"
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
            // 片假名
            "ツ" -> "ッ"
            "ッ" -> "ヅ"
            "ヅ" -> "ツ"
            "カ" -> "ガ"
            "ガ" -> "カ"
            "キ" -> "ギ"
            "ギ" -> "キ"
            "ク" -> "グ"
            "ぐ" -> "ク"
            "ケ" -> "ゲ"
            "ゲ" -> "ケ"
            "コ" -> "ゴ"
            "ゴ" -> "コ"
            "サ" -> "ザ"
            "ザ" -> "サ"
            "シ" -> "ジ"
            "ジ" -> "シ"
            "ス" -> "ズ"
            "ズ" -> "ス"
            "セ" -> "ゼ"
            "ゼ" -> "セ"
            "ソ" -> "ゾ"
            "ゾ" -> "ソ"
            "タ" -> "ダ"
            "ダ" -> "タ"
            "チ" -> "ヂ"
            "ヂ" -> "チ"
            "テ" -> "デ"
            "デ" -> "テ"
            "ト" -> "ド"
            "ド" -> "ト"
            "ハ" -> "バ"
            "バ" -> "パ"
            "力" -> "ハ"
            "ヒ" -> "ビ"
            "び" -> "ピ"
            "ピ" -> "ヒ"
            "フ" -> "ブ"
            "ブ" -> "プ"
            "プ" -> "フ"
            "ヘ" -> "ベ"
            "ベ" -> "ペ"
            "ペ" -> "ヘ"
            "ホ" -> "ボ"
            "ボ" -> "ポ"
            "ポ" -> "ホ"
            "ヤ" -> "ャ"
            "ャ" -> "ヤ"
            "ユ" -> "ュ"
            "ュ" -> "ユ"
            "ヨ" -> "ョ"
            "ョ" -> "ヨ"
            else -> char
        }
    }

    // ✨ 修复 1 & 6：多重条件快速检索，零垃圾匹配，毫秒级响应
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
                val exactMatches = mutableListOf<String>()
                val fuzzyMatches = mutableListOf<String>()
                
                if (isDictionaryLoaded) {
                    for (entry in dictionaryList) {
                        val originalWord = entry[0]
                        val searchWord = entry[1]
                        val originalDef = entry[2]
                        val searchDef = entry[3]
                        
                        // 彻底避免符号误伤，只匹配纯词和纯释义文本
                        if (searchWord.contains(currentInput, ignoreCase = true) || 
                            searchDef.contains(currentInput, ignoreCase = true)) {
                            
                            val formattedDef = originalDef.replace("\\n", "\n").trim()
                            val displayString = "【$originalWord】\n$formattedDef"
                            
                            // 核心优化：完美精准相等的词排最前显示
                            if (searchWord == currentInput) {
                                exactMatches.add(displayString)
                            } else {
                                fuzzyMatches.add(displayString)
                            }
                            
                            if ((exactMatches.size + fuzzyMatches.size) >= 15) break
                        }
                    }
                }
                exactMatches + fuzzyMatches
            }
            filteredTexts = results
            updateDisplayResult()
        }
    }

    // ✨ 修复 4：利用富文本 Span 为查出的每一条词条背景加入底色阴影进行物理区隔
    private fun updateDisplayResult() {
        if (filteredTexts.isEmpty()) {
            display.text = "未找到匹配词条\n(输入: $currentInput)"
            return
        }
        
        // 拼接每组词条并带有物理双换行隔开
        val combined = filteredTexts.joinToString("\n\n")
        val spannable = SpannableString(combined)
        
        val goldColor = 0xFFFFD700.toInt()
        val itemBgColor = 0x1AFFFFFF.toInt() // 优雅清爽的白色微透亮微阴影作为词条大背景

        // 智能为搜索高亮上色
        if (currentInput.length <= combined.length) {
            spannable.setSpan(ForegroundColorSpan(goldColor), 0, currentInput.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // 动态识别各独立词条的首尾索引，精准染上多条隔离色块底色
        var currentIndex = 0
        for (text in filteredTexts) {
            val start = currentIndex
            val end = currentIndex + text.length
            if (end <= spannable.length) {
                spannable.setSpan(BackgroundColorSpan(itemBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            currentIndex = end + 2 // 跨越掉 \n\n 两个字符的分隔线
        }
        
        display.text = spannable
    }

    override fun onDestroy() {
        super.onDestroy()
        dictionaryList.clear()
    }
}
