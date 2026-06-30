package com.edge.llm.server.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.edge.llm.server.util.ServerConsole
import java.io.File
import java.io.FileOutputStream

object UriHelper {

    /**
     * Resolves a Content URI (from ACTION_OPEN_DOCUMENT) to an absolute file path.
     * 
     * Since LiteRT-LM expects a filesystem path string, we try to resolve it directly.
     * If direct resolution fails, we copy the file to a cache file as a fallback.
     */
    fun resolveUriToPath(context: Context, uri: Uri): String? {
        // 1. Check if the URI is a file scheme
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path
        }

        // 2. Try direct resolution under MANAGE_EXTERNAL_STORAGE
        var resolvedPath: String? = null

        // Try standard querying of the _data column
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex != -1) {
                        resolvedPath = cursor.getString(columnIndex)
                    }
                }
            }
        } catch (e: Exception) {
            ServerConsole.log("UriHelper: Querying _data failed: ${e.message}")
        }

        if (resolvedPath != null && File(resolvedPath!!).exists()) {
            ServerConsole.log("UriHelper: Resolved path via _data query: $resolvedPath")
            return resolvedPath
        }

        // Try document contract parsing for com.android.externalstorage.documents
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if (uri.authority == "com.android.externalstorage.documents") {
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        resolvedPath = "/storage/emulated/0/$relativePath"
                    } else {
                        // Handle secondary SD Cards if present
                        resolvedPath = "/storage/$type/$relativePath"
                    }
                }
            } else if (uri.authority == "com.android.providers.downloads.documents") {
                if (docId.startsWith("raw:")) {
                    resolvedPath = docId.substring(4)
                } else {
                    // Fallback to query content uri
                    val contentUri = Uri.parse("content://downloads/public_downloads/$docId")
                    resolvedPath = queryDataColumn(context, contentUri, null, null)
                }
            }
        }

        if (resolvedPath != null && File(resolvedPath!!).exists()) {
            ServerConsole.log("UriHelper: Resolved path via document parsing: $resolvedPath")
            return resolvedPath
        }

        // 3. Query document name and size to check for a local match (Zero-Copy)
        var displayName = "model.litertlm"
        var fileSize = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            ServerConsole.log("UriHelper: Querying details failed: ${e.message}")
        }

        // Try to find a local match in local model folders to avoid copying
        try {
            ServerConsole.log("UriHelper: Checking for local copy of $displayName (size=$fileSize) to avoid copying...")
            val localModels = com.edge.llm.server.model.ModelManager.listLocalModels()
            val match = localModels.firstOrNull { file ->
                file.name == displayName && (fileSize <= 0 || file.length() == fileSize)
            }
            if (match != null) {
                ServerConsole.log("UriHelper: Found local match (Zero-Copy): ${match.absolutePath}")
                return match.absolutePath
            }
        } catch (e: Exception) {
            ServerConsole.log("UriHelper: Match check failed: ${e.message}")
        }

        // 4. Fallback to copy the file to the app's internal cache if no local match is found
        ServerConsole.log("UriHelper: No local match found. Copying model file to private cache folder (might take time)...")
        try {
            // Create target file in internal storage cache
            val cacheDir = File(context.cacheDir, "models")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tempFile = File(cacheDir, displayName)
            
            // Perform stream copy
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (tempFile.exists()) {
                ServerConsole.log("UriHelper: Copied file to cache successfully: ${tempFile.absolutePath}")
                return tempFile.absolutePath
            }
        } catch (e: Exception) {
            ServerConsole.log("UriHelper: Failed to copy file: ${e.message}")
        }

        return null
    }

    private fun queryDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        val projection = arrayOf("_data")
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow("_data")
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
}
