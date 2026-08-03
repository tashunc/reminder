package com.reminder.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val FILE_NAME = "tasks.json"
private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class DriveConflictException : IOException("tasks.json changed on Drive since it was last read")

@Serializable
private data class DriveFileList(val files: List<DriveFileMetadata> = emptyList())

@Serializable
private data class DriveFileMetadata(val id: String? = null, val headRevisionId: String? = null)

/**
 * Talks directly to the Drive REST API v3, scoped to the app-private
 * `appDataFolder` (see docs/drive-storage.md). Every write replaces the
 * whole tasks.json body; callers are responsible for the read-modify-write
 * + retry-on-conflict loop described in docs/sync.md (see TaskRepository).
 */
class DriveTaskStore(private val client: OkHttpClient = OkHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true }

    data class RemoteFile(val fileId: String?, val headRevisionId: String?, val taskFile: TaskFile)

    suspend fun fetch(accessToken: String): RemoteFile = withContext(Dispatchers.IO) {
        val fileId = findFileId(accessToken)
            ?: return@withContext RemoteFile(fileId = null, headRevisionId = null, taskFile = TaskFile())

        val request = Request.Builder()
            .url("$FILES_ENDPOINT/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive read failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            val taskFile = if (body.isBlank()) TaskFile() else json.decodeFromString(body)
            RemoteFile(fileId, headRevisionId(accessToken, fileId), taskFile)
        }
    }

    /**
     * Writes [taskFile]. If [fileId] and [expectedHeadRevisionId] are both
     * non-null, fails with [DriveConflictException] when the file's current
     * revision no longer matches (optimistic concurrency — docs/sync.md).
     * Pass a null [fileId] to create tasks.json for the first time.
     */
    suspend fun write(
        accessToken: String,
        fileId: String?,
        expectedHeadRevisionId: String?,
        taskFile: TaskFile,
    ): RemoteFile = withContext(Dispatchers.IO) {
        if (fileId != null && expectedHeadRevisionId != null) {
            if (headRevisionId(accessToken, fileId) != expectedHeadRevisionId) {
                throw DriveConflictException()
            }
        }

        val body = json.encodeToString(taskFile).toRequestBody(JSON_MEDIA_TYPE)
        val request = if (fileId == null) {
            val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
                .toRequestBody(JSON_MEDIA_TYPE)
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.MIXED)
                .addPart(MultipartBody.Part.create(metadata))
                .addPart(MultipartBody.Part.create(body))
                .build()
            Request.Builder()
                .url("$UPLOAD_ENDPOINT?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()
        } else {
            Request.Builder()
                .url("$UPLOAD_ENDPOINT/$fileId?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .patch(body)
                .build()
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive write failed: ${response.code}")
            val resolvedFileId = fileId
                ?: findFileId(accessToken)
                ?: error("Drive write succeeded but the file could not be located afterwards")
            RemoteFile(resolvedFileId, headRevisionId(accessToken, resolvedFileId), taskFile)
        }
    }

    private fun findFileId(accessToken: String): String? {
        val request = Request.Builder()
            .url("$FILES_ENDPOINT?spaces=appDataFolder&q=name='$FILE_NAME'&fields=files(id)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive lookup failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            return json.decodeFromString<DriveFileList>(body).files.firstOrNull()?.id
        }
    }

    private fun headRevisionId(accessToken: String, fileId: String): String? {
        val request = Request.Builder()
            .url("$FILES_ENDPOINT/$fileId?fields=headRevisionId")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return json.decodeFromString<DriveFileMetadata>(body).headRevisionId
        }
    }
}
