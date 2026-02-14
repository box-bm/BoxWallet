package com.boxdev.boxwallet.data.remote

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdTime: Long,
    val size: Long
)

@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var driveService: Drive? = null

    fun initialize(accountEmail: String) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccountName = accountEmail
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("BoxWallet")
            .build()
    }

    suspend fun uploadBackup(fileName: String, json: String): DriveBackupFile = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        val fileMetadata = DriveFile().apply {
            name = fileName
            parents = listOf("appDataFolder")
        }
        val mediaContent = ByteArrayContent.fromString("application/json", json)
        val file = drive.files().create(fileMetadata, mediaContent)
            .setFields("id, name, createdTime, size")
            .execute()

        DriveBackupFile(
            id = file.id,
            name = file.name,
            createdTime = file.createdTime?.value ?: System.currentTimeMillis(),
            size = file.getSize()?.toLong() ?: json.toByteArray().size.toLong()
        )
    }

    suspend fun listBackups(): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name, createdTime, size)")
            .setOrderBy("createdTime desc")
            .execute()

        result.files?.map { file ->
            DriveBackupFile(
                id = file.id,
                name = file.name,
                createdTime = file.createdTime?.value ?: 0L,
                size = file.getSize()?.toLong() ?: 0L
            )
        } ?: emptyList()
    }

    suspend fun downloadBackup(fileId: String): String = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        val outputStream = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        outputStream.toString("UTF-8")
    }

    suspend fun deleteBackup(fileId: String) = withContext(Dispatchers.IO) {
        val drive = requireDriveService()
        drive.files().delete(fileId).execute()
    }

    fun clear() {
        driveService = null
    }

    private fun requireDriveService(): Drive {
        return driveService ?: throw IllegalStateException("Drive service not initialized. Call initialize() first.")
    }
}
