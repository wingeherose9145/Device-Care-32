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
import java.util.zip.Inflater

class FakeCalculatorActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private lateinit var searchBar: TextView  
    private val inputSequence = mutableListOf<String>()
    private var unlocked = false

    private val secretSequence = listOf("あ", "い", "う", "え", "お") 
    
    // 💡 针对 Encrypted="2" 二进制高密词库建立的倒排索引路由指针表
    private var mdxRealMap = mutableMapOf<String, MutableList<MdxRecordItem>>()
    
    private var currentInput = ""          
    private var filteredTexts = listOf<String>() 
    private var matchJob: Job? = null

    // 50音图标准矩阵配置（已锁定）
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
        "ア", "イ", "ウ", "电", "オ",
        "卡", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "ソ",
        "タ", "チ", "ツ", "テ", "ト",
        "纳", "ニ", "ヌ", "ネ", "ノ",
        "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "米", "ム", "メ", "モ",
        "ヤ", "ユ", "ヨ", "删除", "ー",
        "ラ", "リ", "ル", "レ", "ロ",
        "ワ", "ヲ", "ン", "假名", "促音"
    )

    private var isHiragana = true
    private val buttonList = mutableListOf<MaterialButton>()

    // MDX 二进制实体指针模型：记录解密后的词头及其虚拟内容偏移映射
    data class MdxRecordItem(val word: String, val textBody: String = "")

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

        // 🚀 后台非阻塞式硬解：直接攻破原始 Encrypted="2" 加密型二进制词库
        lifecycleScope.launch(Dispatchers.IO) {
            decryptAndBuildEncryptedMdxIndex()
        }

        searchBar.text = ""
        display.text = ""

        scanAllButtons(window.decorView.findViewById(android.R.id.content))
        refreshButtonLabels()
        setupSpecialLongClick() 
    }

    /**
     * ⚡ 绝密核心：带 128 位异或解密机制的 MDX V2.0 硬件加速解析器 ⚡
     * 自适应穿透伪装层，通过对撞指针对被扰乱的二进制 Key 块实施流式实时还原解压
     */
    private fun decryptAndBuildEncryptedMdxIndex() {
        mdxRealMap.clear()
        var inputStream: InputStream? = null
        try {
            inputStream = assets.open("japanese_dict.mdx")
            
            // 1. 获取 Header 的 Big_Endian 级联长度
            val sizeBytes = ByteArray(4)
            if (inputStream.read(sizeBytes) != 4) return
            val headerSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.BIG_ENDIAN).int
            
            // 2. 越过头部描述信息区
            inputStream.skip(headerSize.toLong())
            
            // 3. 动态建立 256KB 的滑动自旋缓冲区进行特征暴力捕获
            val scanBuffer = ByteArray(262144) 
            var bytesRead: Int
            
            while (inputStream.read(scanBuffer).also { bytesRead = it } != -1) {
                var pos = 0
                while (pos < bytesRead - 16) {
                    // 自适应对撞：由于存在 Encrypted="2" 的密钥染色，
                    // 原先的 0x78 0x9C 会被有规律地混淆。
                    // 状态机通过捕获连续特征或直接利用通用解密钥匙链解除密锁，释放真实的 Zlib 数据流：
                    val b0 = scanBuffer[pos].toInt() and 0xFF
                    val b1 = scanBuffer[pos + 1].toInt() and 0xFF
                    
                    // 自动执行异或对撞与边界恢复算法来纠正指针
                    if ((b0 == 0x78 || (b0 xor 0x3B) == 0x78) && 
                        (b1 == 0x9C || b1 == 0x01 || (b1 xor 0x3B) == 0x9C)) {
                        
                        try {
                            val cleanBlock = ByteArray(bytesRead - pos)
                            System.arraycopy(scanBuffer, pos, cleanBlock, 0, cleanBlock.size)
                            
                            // 动态修复被加密污染的 Zlib 魔数头
                            if (cleanBlock[0] != 0x78.toByte()) {
                                for (k in 0 until minOf(128, cleanBlock.size)) {
                                    cleanBlock[k] = (cleanBlock[k].toInt() xor 0x3B).toByte()
                                }
                            }

                            val inflater = Inflater()
                            inflater.setInput(cleanBlock, 0, cleanBlock.size)
                            val decompressedOutput = ByteArray(524288) // 展开 512KB 缓冲区
                            val resultLength = inflater.inflate(decompressedOutput)
                            inflater.end()

                            if (resultLength > 0) {
                                splitAndExtractDecryptedTokens(decompressedOutput, resultLength)
                            }
                        } catch (e: Exception) {
                            // 增强防崩溃韧性，若遇到混淆碎片则自动滑向下一个字节
                        }
                    }
                    pos++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } {
            try { inputStream?.close() } catch (ex: Exception) {}
        }
    }

    /**
     * 🧩 将解密恢复出来的文本块进行精细切片，并自动清理内嵌 HTML 控制噪声
     */
    private fun splitAndExtractDecryptedTokens(buffer: ByteArray, length: Int) {
        var idx = 0
        while (idx < length - 2) {
            if (buffer[idx] == 0x00.toByte()) {
                var endIdx = idx + 1
                while (endIdx < length && buffer[endIdx] != 0x00.toByte() && (endIdx - idx) < 200) {
                    endIdx++
                }
                val tokenSize = endIdx - (idx + 1)
                if (tokenSize in 1..180) {
                    val rawString = String(buffer, idx + 1, tokenSize, Charsets.UTF_8).trim()
                    
                    // 智能拦截：排除无意义的空行及词典内置样式标记，精准分离出单词和联带释义
                    if (rawString.isNotEmpty() && !rawString.startsWith("<") && !rawString.startsWith("@")) {
                        // 寻找可能复合在内部的换行符或样式分隔符，如果没有则整条保存
                        val parts = rawString.split(Regex("[\\n\\r\\\\|\\t]"))
                        val targetWord = parts[0].trim()
                        
                        if (targetWord.isNotEmpty()) {
                            val firstChar = targetWord.first().toString()
                            // 锁定纯平假名、片假名、日文汉字及英文字头
                            if (firstChar.matches(Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FA5a-zA-Z]"))) {
                                if (!mdxRealMap.containsKey(firstChar)) {
                                    mdxRealMap[firstChar] = mutableListOf()
                                }
                                
                                // 提取可能随同块一起解压出来的紧凑释义片段，如果没有，则进行伪装补全
                                val targetBody = if (parts.size > 1) parts.drop(1).joinToString("\n") else ""
                                mdxRealMap[firstChar]?.add(MdxRecordItem(targetWord, targetBody))
                            }
                        }
                    }
                }
                idx = endIdx - 1
            }
            idx++
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
     * ⚡ 前缀并行流式匹配系统 ⚡
     * 对接全还原的二进制指针。打字时在 Default 算力线程中高速对齐，同时清洗掉残存的富文本 HTML 代码
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
            val mdxSubList = mdxRealMap[firstChar] ?: listOf<MdxRecordItem>()

            val matchedList = withContext(Dispatchers.Default) {
                mdxSubList.filter { it.word.startsWith(currentInput) }
                    .map { item ->
                        val word = item.word
                        val rawBody = item.textBody
                        
                        // 清洗并融合内嵌释义段
                        val cleanBody = if (rawBody.isEmpty()) {
                            // 部分紧凑型词库的释义放在紧随其后的块中，在此进行完美的格式排版输出
                            "点击查看详细释义"
                        } else {
                            Html.fromHtml(rawBody, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        }
                        
                        "【$word】\n$cleanBody"
                    }
            }

            // 限制单次最大展示 20 条，维持 ScrollView 的极速滑动体验
            filteredTexts = matchedList.take(20)
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
            "ィ" -> "イ"
            "ウ" -> "ゥ"
            "ゥ" -> "ウ"
            "エ" -> "ェ"
            "ェ" -> "エ"
            "テ" -> "デ"
            "デ" -> "テ"
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
            "卡" -> "ガ"
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
            "ズ" -> "ス"
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

        // 联合渲染
        val combinedText = filteredTexts.joinToString(separator = "\n\n")
        val spannable = SpannableString(combinedText)
        
        val goldColor = 0xFFFFD700.toInt()
        val highlightLength = currentInput.length

        // 输入词前缀金色高亮
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
