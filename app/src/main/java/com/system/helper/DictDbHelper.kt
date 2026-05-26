package com.system.helper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.FileOutputStream

class DictDbHelper(private val context: Context) {

    private val dbName = "abc.db"

    private var db: SQLiteDatabase? = null

    init {
        copyDatabase()
        openDatabase()
    }

    /**
     * 首次启动复制数据库
     */
    private fun copyDatabase() {

        val dbFile = context.getDatabasePath(dbName)

        if (!dbFile.exists()) {

            dbFile.parentFile?.mkdirs()

            context.assets.open(dbName).use { input ->

                FileOutputStream(dbFile).use { output ->

                    input.copyTo(output)

                }
            }
        }
    }

    /**
     * 打开数据库
     */
    private fun openDatabase() {

        val path = context.getDatabasePath(dbName).path

        db = SQLiteDatabase.openDatabase(
            path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    /**
     * 前缀搜索
     */
    fun search(query: String): List<DictItem> {

        val result = mutableListOf<DictItem>()

        if (query.isBlank()) {
            return result
        }

        val cursor = db?.rawQuery(
            """
            SELECT word, reading, html
            FROM dict
            WHERE word LIKE ?
            LIMIT 50
            """.trimIndent(),
            arrayOf("$query%")
        )

        cursor?.use {

            while (it.moveToNext()) {

                result.add(
                    DictItem(
                        word = it.getString(0),
                        reading = it.getString(1),
                        html = it.getString(2)
                    )
                )
            }
        }

        return result
    }

    /**
     * 精确获取词条
     */
    fun getWord(word: String): DictItem? {

        val cursor = db?.rawQuery(
            """
            SELECT word, reading, html
            FROM dict
            WHERE word = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(word)
        )

        cursor?.use {

            if (it.moveToFirst()) {

                return DictItem(
                    word = it.getString(0),
                    reading = it.getString(1),
                    html = it.getString(2)
                )
            }
        }

        return null
    }
}
