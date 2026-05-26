package com.system.helper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class DictDbHelper(private val context: Context) {

    private val dbName = "abc.db"

    private var db: SQLiteDatabase? = null

    init {
        openDatabase()
    }

    /**
     * 打开数据库
     */
    private fun openDatabase() {

        val dbFile = context.getDatabasePath(dbName)

        if (!dbFile.exists()) {
            return
        }

        db = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    /**
     * 数据库是否存在
     */
    fun isDatabaseExists(): Boolean {

        val dbFile = context.getDatabasePath(dbName)

        return dbFile.exists()
    }

    /**
     * 获取数据库文件
     */
    fun getDatabaseFile(): File {

        return context.getDatabasePath(dbName)
    }

    /**
     * 搜索词条
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
     * 获取完整词条
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
