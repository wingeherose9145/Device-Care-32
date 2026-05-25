package com.system.helper

import android.content.Intent
import android.os.Bundle
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

    // 内存数据结构：高效率 K-V 词典（使用高效的 HashMap 应对 30M+ 级别的大文本内存常驻）
    private val dictionaryMap = mutableMapOf<String, String>()
    private var isDictionaryLoaded = false

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
        "マ", "ミ", "ム", "美", "モ", "ヤ", "ユ", "ヨ", "删除", "ー",
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

        // 异步流式加载清洗后的 32M 纯文本词库
        lifecycleScope.launch(Dispatchers.IO) { loadTxtDatabase() }

        searchBar.setOnClickListener { currentInput = ""; matchAndFilter() }
        display.setOnLongClickListener { currentInput = ""; matchAndFilter(); true }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    // 针对大文件优化的流式装载逻辑
    private fun loadTxtDatabase() {
        try {
            Log.d("TXT_DB", "正在从 assets 中加载 32M 精简版 dict.txt...")
            val startTime = System.currentTimeMillis()
            
            assets.open("dict.txt").use { inputStream ->
                // 使用较大的缓冲区（64KB）提升 IO 读取大文件的性能
                BufferedReader(InputStreamReader(inputStream, "UTF-8"), 65536).use { reader ->
                    var line: String?
                    var count = 0
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty()) continue
                        
                        // 按照分隔符 ||| 切分单词和释义
                        val parts = trimmed.split("|||")
                        if (parts.size >= 2) {
                            val word = parts[0].trim()
                            val definition = parts[1].trim()
                            dictionaryMap[word] = definition
                            count++
                        }
                    }
                    isDictionaryLoaded = true
                    val timeTaken = System.currentTimeMillis() - startTime
                    Log.d("TXT_DB", "✅ 词库成功装载到内存！耗时: ${timeTaken}ms, 总计: $count 条词条")
                }
            }
        } catch (e: Exception) {
            Log.e("TXT_DB", "❌ 词库加载失败，请检查 assets 目录下是否存在 dict.txt", e)
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
                
                if (isDictionaryLoaded) {
                    var matchCount = 0
                    for ((word, definition) in dictionaryMap) {
                        // 模糊匹配：判断输入的假名是否在单词里出现，或者包含在释义中
                        if (word.contains(currentInput, ignoreCase = true) || 
                            definition.contains(currentInput, ignoreCase = true)) {
                            
                            // 将清洗时保留的文本换行占位符 \\n 重新替换还原为系统真正可解析的 \n
                            val cleanDef = definition.replace("\\n", "\n").trim()
                            list.add("【$word】\n$cleanDef")
                            
                            matchCount++
                            if (matchCount >= 15) break // 限制最多展示 15 条，防止极端匹配下内存过载
                        }
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
        dictionaryMap.clear()
    }
}
