package com.system.helper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.URL

object Downloader {

    private const val DB_URL =
        "https://github.com/wingeherose9145/Device-Care-32/releases/download/v2.0/abc.db"

    suspend fun downloadDatabase(context: Context): Boolean {

        return withContext(Dispatchers.IO) {

            try {

                val dbFile = context.getDatabasePath("abc.db")

                dbFile.parentFile?.mkdirs()

                URL(DB_URL).openStream().use { input ->

                    FileOutputStream(dbFile).use { output ->

                        input.copyTo(output)

                    }
                }

                true

            } catch (e: Exception) {

                e.printStackTrace()

                false
            }
        }
    }
}
