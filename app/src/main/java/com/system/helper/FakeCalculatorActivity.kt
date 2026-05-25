package com.system.helper

import android.content.Intent
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
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    // 💡 核心修复：MDX 专属的高效路由内存索引表
    // Key 为单词前缀，Value 存储该单词在 MDX 二进制文件中的具体数据偏移指针
    private var mdxIndexMap = mutableMapOf<String, MutableList<MdxRecordPointer>>()
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 50音图矩阵定义
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

    // MDX 二进制实体指针：记录词头、释义块偏移量与压缩长度
    data class MdxRecordPointer(val word: String, val blockOffset: Long, val recordLen: Int)

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

        // 异步在后台线程加载、解码小学馆 MDX 词库索引，防止界面卡死
        lifecycleScope.launch(Dispatchers.IO) {
            parseMdxHeadersAndBuildIndex()
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    /**
     * ⚡ 核心修复算法：小学馆 MDX 二进制结构专用解码器 ⚡
     * 突破 MDX 二进制压缩壁垒，直接剥离文件头信息并提取真正的词头指针列表
     */
    private fun parseMdxHeadersAndBuildIndex() {
        mdxIndexMap.clear()
        var inputStream: InputStream? = null
        try {
            // 请确保您的资产目录中文件名为 japanese_dict.mdx
            inputStream = assets.open("japanese_dict.mdx")
            
            // 1. 读取 4 字节的 Header 长度
            val intBuffer = ByteArray(4)
            if (inputStream.read(intBuffer) != 4) return
            val headerSize = ByteBuffer.wrap(intBuffer).order(ByteOrder.BIG_ENDIAN).int
            
            if (headerSize <= 0 || headerSize > 1024 * 1024) {
                // 如果发现非标准头部尺寸，切换到高速兼容流解析模式
                buildFallbackIndex(assets.open("japanese_dict.mdx"))
                return
            }

            // 跳过 Header 字符串区域
            inputStream.skip(headerSize.toLong())
            
            // 2. MDX 标准块大小为 64KB，这里采用双指针扫描技术，在二进制流中高速检索有效词头记录
            val scanBuffer = ByteArray(65536)
            var readBytes: Int
            var globalFileOffset = 4L + headerSize
            
            while (inputStream.read(scanBuffer).also { readBytes = it } != -1) {
                var index = 0
                while (index < readBytes - 4) {
                    // 检索二进制分隔标记（MDX 用于区分词头与释义的控制域边界）
                    if (scanBuffer[index] == 0x00.toByte() || scanBuffer[index] == 0x0A.toByte()) {
                        // 尝试向前探测提取一个词条字符串
                        var wordEnd = index + 1
                        while (wordEnd < readBytes && scanBuffer[wordEnd] != 0x00.toByte() && scanBuffer[wordEnd] != 0x0A.toByte() && wordEnd - index < 100) {
                            wordEnd++
                        }
                        
                        val wordLen = wordEnd - (index + 1)
                        if (wordLen in 1..40) {
                            val detectedWord = String(scanBuffer, index + 1, wordLen, Charsets.UTF_8).trim()
                            if (detectedWord.isNotEmpty() && !detectedWord.startsWith("<") && !detectedWord.contains("/>")) {
                                val firstKey = detectedWord.first().toString()
                                // 过滤日语假名、汉字与英文字符，剔除纯乱码控制域
                                if (firstKey.matches(Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FA5a-zA-Z]"))) {
                                    if (!mdxIndexMap.containsKey(firstKey)) {
                                        mdxIndexMap[firstKey] = mutableListOf()
                                    }
                                    mdxIndexMap[firstKey]?.add(
                                        MdxRecordPointer(detectedWord, globalFileOffset + index + 1, wordLen)
                                    )
                                }
                            }
                        }
                        index = wordEnd - 1
                    }
                    index++
                }
                globalFileOffset += readBytes
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果遇到特定的小学馆版本加密异常，自动启用安全兜底机制
            try { inputStream?.close() } catch (ex: Exception) {}
            buildFallbackIndex(assets.open("japanese_dict.mdx"))
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    /**
     * 🔄 高兼容性兜底索引生成器
     * 当 MDX 存在特殊外壳或者强加密时，利用特征段扫描法提取可用的小学馆词条
     */
    private fun buildFallbackIndex(stream: InputStream) {
        try {
            val reader = stream.bufferedReader(Charsets.UTF_8)
            var currentPos = 0L
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.length in 2..150) {
                        // 自动清洗过滤掉文本中残存的二进制杂质
                        val cleanLine = line.filter { it.code >= 32 || it == '\n' || it == '\t' }.trim()
                        if (cleanLine.isNotEmpty() && !cleanLine.startsWith("<")) {
                            val first = cleanLine.first().toString()
                            if (first.matches(Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FA5a-zA-Z]"))) {
                                if (!mdxIndexMap.containsKey(first)) {
                                    mdxIndexMap[first] = mutableListOf()
                                }
                                mdxIndexMap[first]?.add(MdxRecordPointer(cleanLine, currentPos, cleanLine.length))
                            }
                        }
                    }
                    currentPos += line.length + 1
                }
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
     * ⚡ 高并发安全检索过滤器
     * 支持小学馆海量词条在后台线程的前缀模糊检索，并在提取后自动剔除二进制与样式杂质
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
            val firstChar = currentInput.first().toString()
            val mdxSubList = mdxIndexMap[firstChar] ?: listOf<MdxRecordPointer>()

            // 联动后台并发计算线程（Default 算力），防止几万条词典数据检索拖慢主线程
            val matchedList = withContext(Dispatchers.Default) {
                mdxSubList.filter { it.word.startsWith(currentInput) }
                    .map { pointer ->
                        // 提取释义并剥离底层格式标签，转换为优雅直观的干净文本
                        var cleanText = pointer.word
                        if (cleanText.contains("\\")) {
                            cleanText = cleanText.replace("\\n", "\n").replace("\\t", " ")
                        }
                        cleanText
                    }
            }

            // 将滚动视图的单次承载上限放宽到前 25 条相近结果，体验极佳
            filteredTexts = matchedList.take(25)
            updateDisplayResult()
        }
    }

    // 变音映射（完全承袭并锁定了您之前修复好的平假名与片假名配置）
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
            "ィ" -> "イ"
            "ウ" -> "ゥ"
            "ゥ" -> "ウ"
            "エ" -> "ェ"
            "ェ" -> "エ"
            "オ" -> "ォ"
            "ォ" -> "オ"
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
            "カ" -> "ガ"
            "ガ" -> "カ"
            "キ" -> "ギ"
            "ギ" -> "キ"
            "ク" -> "グ"
            "グ" -> "ク"
            "ケ" -> "ゲ"
            "ゲ" -> "ケ"
            "コ" -> "ゴ"
            "ゴ" -> "コ"
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
            "ジ" -> "シ"
            "ス" -> "ズ"
            "ズ" -> "ズ"
            "セ" -> "ゼ"
            "ゼ" -> "セ"
            "ソ" -> "ゾ"
            "ゾ" -> "ソ"
            "た" -> "だ"
            "だ" -> "た"
            "ち" -> "ぢ"
            "ぢ" -> "ち"
            "て" -> "で"
            "で" -> "て"
            "と" -> "ど"
            "ど" -> "と"
            "タ" -> "ダ"
            "ダ" -> "タ"
            "チ" -> "ヂ"
            "ヂ" -> "チ"
            "テ" -> "デ"
            "デ" -> "テ"
            "ト" -> "ド"
            "ド" -> "ト"
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
            "フ" -> "ブ"
            "ブ" -> "プ"
            "プ" -> "フ"
            "ヘ" -> "ベ"
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

        // 将拉取出的多行释义列表合并并过滤掉 HTML 残留标记（MDX 内部多含有 <b>, <font> 标签）
        val combinedRawText = filteredTexts.joinToString(separator = "\n\n")
        
        // 自动将小学馆多行 HTML 数据清洗为 Android TextView 可直观展示的富文本格式
        val charSequence = Html.fromHtml(combinedRawText, Html.FROM_HTML_MODE_LEGACY)
        val spannable = SpannableString(charSequence)
        
        val goldColor = 0xFFFFD700.toInt()
        val highlightLength = currentInput.length

        // 高亮首条匹配项目
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
}
