package com.system.helper

import android.content.Intent
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
import java.io.File
import java.io.FileOutputStream
import mdict.mdict  // ← mdict-java 核心类

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 50音图
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
        "サ", "シ", "ス", "セ", "ソ",
        "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ",
        "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "ミ", "ム", "メ", "モ",
        "ヤ", "ユ", "ヨ", "删除", "ー",
        "ラ", "リ", "ル", "レ", "ロ",
        "ワ", "ヲ", "ン", "假名", "促音"
    )

    private var isHiragana = true
    private val buttonList = mutableListOf<MaterialButton>()

    // MDX 字典实例
    private var mdxDict: mdict? = null

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

        // 异步加载 MDX
        lifecycleScope.launch(Dispatchers.IO) {
            setupMdxDictionary()
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    /**
     * 加载 japanese_dict.mdx
     */
    private fun setupMdxDictionary() {
        try {
            val mdxFile = File(getExternalFilesDir(null), "japanese_dict.mdx")
            
            // 从 assets 复制文件
            if (!mdxFile.exists()) {
                Log.d("MDX", "从 assets 复制 japanese_dict.mdx...")
                assets.open("japanese_dict.mdx").use { input ->
                    FileOutputStream(mdxFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            mdxDict = mdict(mdxFile.absolutePath)
            Log.d("MDX", "MDX 字典加载成功！总词条数: ${mdxDict?.entryCount() ?: 0}")
            
        } catch (e: Exception) {
            Log.e("MDX", "MDX 加载失败", e)
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
                    currentInput = currentInput.substring(0, currentInput.length - 1) + convertToTransformChar(lastChar)
                    matchAndFilter()
                }
            }
            else -> {
                currentInput += value
                inputSequence.add(value)
                if (inputSequence.size > 5) inputSequence.removeAt(0)
                if (inputSequence == secretSequence) unlocked = true

                matchAndFilter()
            }
        }
    }

    /**
     * 使用 mdict-java 进行查询
     */
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
            val input = currentInput
            
            val matchedList = withContext(Dispatchers.Default) {
                val results = mutableListOf<String>()
                val dict = mdxDict
                
                if (dict != null) {
                    try {
                        // 使用 lookUp 查找
                        val pos = dict.lookUp(input, true)  // 严格匹配
                        if (pos >= 0) {
                            val word = dict.getEntryAt(pos)
                            val htmlContent = dict.getRecordAt(pos)
                            val cleanBody = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                            results.add("【$word】\n$cleanBody")
                        } else {
                            // 如果严格匹配失败，尝试模糊搜索前几个
                            val fuzzyResults = dict.flowerFindAllKeys(input, 10)
                            for (i in 0 until minOf(8, fuzzyResults.size)) {
                                val key = fuzzyResults[i]
                                val p = dict.lookUp(key, true)
                                if (p >= 0) {
                                    val htmlContent = dict.getRecordAt(p)
                                    val cleanBody = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                    results.add("【$key】\n$cleanBody")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MDX", "查询失败: $input", e)
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
            "つ" -> "っ"; "っ" -> "つ"
            "や" -> "ゃ"; "ゃ" -> "や"
            "ゆ" -> "ゅ"; "ゅ" -> "ゆ"
            "よ" -> "ょ"; "ょ" -> "よ"
            "あ" -> "ぁ"; "ぁ" -> "あ"
            "い" -> "ぃ"; "ぃ" -> "い"
            "う" -> "ぅ"; "ぅ" -> "う"
            "え" -> "ぇ"; "ぇ" -> "え"
            "お" -> "ぉ"; "ぉ" -> "お"
            else -> char
        }
    }

    private fun updateDisplayResult() {
        if (currentInput.isEmpty() || filteredTexts.isEmpty()) {
            display.text = ""
            return
        }

        val combinedText = filteredTexts.joinToString(separator = "\n\n")
        val spannable = SpannableString(combinedText)
        
        val goldColor = 0xFFFFD700.toInt()
        val highlightLength = currentInput.length

        if (highlightLength > 0 && highlightLength <= spannable.length) {
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
            mdxDict?.close()
        } catch (e: Exception) {}
    }
}
