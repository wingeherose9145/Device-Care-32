package com.system.helper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.FileOutputStream

class DictDbHelper(private val context: Context) {

    private val dbName = "abc.db"
    private var db: SQLiteDatabase? = null

    init {
        copyDbIfNeeded()
        openDb()
    }

    private fun copyDbIfNeeded() {
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

    private fun openDb() {
        val path = context.getDatabasePath(dbName).path
        db = SQLiteDatabase.openDatabase(
            path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    fun search(query: String): List<DictItem> {
        val list = mutableListOf<DictItem>()

        val cursor = db?.rawQuery(
            "SELECT word, reading, html FROM dict WHERE word LIKE ? LIMIT 50",
            arrayOf("$query%")
        )

        cursor?.use {
            while (it.moveToNext()) {
                list.add(
                    DictItem(
                        word = it.getString(0),
                        reading = it.getString(1),
                        html = it.getString(2)
                    )
                )
            }
        }

        return list
    }

    fun getWord(word: String): DictItem? {
        val cursor = db?.rawQuery(
            "SELECT word, reading, html FROM dict WHERE word = ? LIMIT 1",
            arrayOf(word)
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return DictItem(
                    it.getString(0),
                    it.getString(1),
                    it.getString(2)
                )
            }
        }

        return null
    }
}
