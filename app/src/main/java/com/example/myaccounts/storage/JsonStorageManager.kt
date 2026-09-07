package com.example.myaccounts.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class JsonStorageManager(private val context: Context) {
    private val dataFileName = "fireball_data.json"
    private val tempFileName = "fireball_data.json.tmp"

    @Synchronized
    fun saveAppData(jsonData: String): Boolean {
        return try {
            val fileDir = context.filesDir
            if (!fileDir.exists()) {
                fileDir.mkdirs()
            }
            val tempFile = File(fileDir, tempFileName)
            val targetFile = File(fileDir, dataFileName)

            FileOutputStream(tempFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    fun loadAppData(): String? {
        return try {
            val targetFile = File(context.filesDir, dataFileName)
            if (targetFile.exists()) {
                targetFile.readText(StandardCharsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
