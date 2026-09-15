package com.example.myaccounts.storage

import android.content.Context
import com.example.myaccounts.model.AppData
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class StorageManager(private val context: Context) {

    private val encryptedBsonFileName = "fireball_data.bson.enc"
    private val tempBsonFileName = "fireball_data.bson.enc.tmp"
    private val legacyJsonFileName = "fireball_data.json"

    private val encryptionManager by lazy { EncryptionManager() }

    @Synchronized
    fun saveAppData(appData: AppData): Boolean {
        return try {
            val fileDir = context.filesDir
            if (!fileDir.exists()) {
                fileDir.mkdirs()
            }

            // 1. Serialize to BSON
            val bsonBytes = BsonStorage.appDataToBson(appData)

            // 2. Encrypt with AES-GCM
            val encryptedBytes = encryptionManager.encrypt(bsonBytes)

            // 3. Write atomically to disk
            val tempFile = File(fileDir, tempBsonFileName)
            val targetFile = File(fileDir, encryptedBsonFileName)

            FileOutputStream(tempFile).use { fos ->
                fos.write(encryptedBytes)
                fos.flush()
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
    fun loadAppData(): AppData {
        return try {
            val targetFile = File(context.filesDir, encryptedBsonFileName)
            if (targetFile.exists()) {
                val encryptedBytes = targetFile.readBytes()
                val decryptedBson = encryptionManager.decrypt(encryptedBytes)
                BsonStorage.bsonToAppData(decryptedBson)
            } else {
                // Check for legacy json to migrate
                val legacyFile = File(context.filesDir, legacyJsonFileName)
                if (legacyFile.exists()) {
                    val jsonStr = legacyFile.readText(StandardCharsets.UTF_8)
                    val migratedData = BsonStorage.jsonToAppData(jsonStr)
                    saveAppData(migratedData)
                    legacyFile.delete() // Clean up unencrypted legacy file
                    migratedData
                } else {
                    AppData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppData()
        }
    }

    @Synchronized
    fun saveAppDataJson(jsonStr: String): Boolean {
        return try {
            val appData = BsonStorage.jsonToAppData(jsonStr)
            saveAppData(appData)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    fun loadAppDataJson(): String {
        return try {
            val appData = loadAppData()
            BsonStorage.appDataToJson(appData)
        } catch (e: Exception) {
            e.printStackTrace()
            "{}"
        }
    }
}
