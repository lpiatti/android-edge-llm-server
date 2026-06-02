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

        // 3. Fallback to copy the file to the app's internal cache
        // Warn the user since LLM files are large
        ServerConsole.log("UriHelper: Direct resolution failed. Copying model file to private cache folder (might take time)...")
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var displayName = "model.litertlm"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = it.getString(nameIndex)
                    }
                }
            }

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
