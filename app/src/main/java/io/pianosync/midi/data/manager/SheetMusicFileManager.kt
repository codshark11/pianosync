package io.pianosync.midi.data.manager

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SheetMusicFileManager {
    private const val TAG = "SheetMusicFileManager"

    /**
     * Finds a matching XML file for a given MIDI file URI.
     * Looks for files with the same name but with .xml extension in the same directory.
     *
     * @param context The application context
     * @param midiFileUri The URI of the MIDI file
     * @return The URI of the matching XML file, or null if not found
     */
    suspend fun findMatchingXmlFile(
        context: Context,
        midiFileUri: Uri
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Extract the filename from the MIDI file URI
            val midiFileName = getFileNameFromUri(context, midiFileUri) ?: return@withContext null
            
            // Remove extension and create XML filename
            val baseName = midiFileName.substringBeforeLast(".")
            val xmlFileName = "$baseName.xml"
            
            Log.d(TAG, "Looking for XML file: $xmlFileName for MIDI: $midiFileName")
            Log.d(TAG, "MIDI file URI - scheme: ${midiFileUri.scheme}, authority: ${midiFileUri.authority}, path: ${midiFileUri.path}")
            
            // Special handling for Downloads provider - use MediaStore to find the file
            // This avoids permission issues with DocumentsContract
            val isDownloadsProvider = midiFileUri.authority == "com.android.providers.downloads.documents" ||
                    midiFileUri.authority?.contains("downloads", ignoreCase = true) == true
            
            Log.d(TAG, "Is Downloads provider: $isDownloadsProvider (authority: ${midiFileUri.authority})")
            
            if (isDownloadsProvider) {
                Log.d(TAG, "Detected Downloads provider, using MediaStore approach")
                val xmlUri = findFileInDownloadsUsingMediaStore(context, xmlFileName)
                if (xmlUri != null) {
                    Log.d(TAG, "Found matching XML file via MediaStore: $xmlUri")
                    return@withContext xmlUri
                } else {
                    Log.w(TAG, "MediaStore query did not find XML file, trying alternative methods")
                    // Try alternative approach for Downloads
                    val altUri = tryFindXmlByDocumentId(context, midiFileUri, xmlFileName)
                    if (altUri != null) {
                        return@withContext altUri
                    }
                    // For Downloads provider, don't try parent directory approach as it requires permissions
                    Log.w(TAG, "Could not find XML file in Downloads using available methods")
                    return@withContext null
                }
            }
            
            // For non-Downloads providers, try the parent directory approach
            // Get the parent directory
            val parentUri = getParentUri(context, midiFileUri)
            if (parentUri == null) {
                Log.w(TAG, "Could not determine parent directory for $midiFileUri")
                return@withContext null
            }
            
            Log.d(TAG, "Parent directory URI: $parentUri")
            
            // Try to find the XML file in the same directory
            val xmlUri = findFileInDirectory(context, parentUri, xmlFileName)
            
            if (xmlUri != null) {
                Log.d(TAG, "Found matching XML file: $xmlUri")
            } else {
                Log.d(TAG, "No matching XML file found for $midiFileName")
            }
            
            xmlUri
        } catch (e: Exception) {
            Log.e(TAG, "Error finding matching XML file", e)
            null
        }
    }

    /**
     * Copies a URI to a temporary file that can be accessed by Verovio.
     * Verovio may require a file path rather than a URI.
     */
    suspend fun copyUriToTempFile(
        context: Context,
        uri: Uri
    ): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(context, uri) ?: "tempfile.xml"
            val tempFile = File(context.cacheDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Copied URI to temp file: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to temp file", e)
            null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            // Try using DocumentFile first (for document URIs)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            documentFile?.name
        } catch (e: Exception) {
            // Fallback to querying content resolver
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
            } catch (e2: Exception) {
                // Last resort: try to extract from URI path
                uri.lastPathSegment
            }
        }
    }

    private fun getParentUri(context: Context, uri: Uri): Uri? {
        return try {
            Log.d(TAG, "Getting parent URI for: $uri")
            
            // For document URIs, get parent
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val documentId = DocumentsContract.getDocumentId(uri)
                Log.d(TAG, "Document ID: $documentId")
                
                val parentId = documentId.substringBeforeLast("/")
                Log.d(TAG, "Parent ID: $parentId")
                
                if (parentId.isNotEmpty()) {
                    // Build parent URI using the same authority
                    val authority = uri.authority
                    val parentUri = DocumentsContract.buildDocumentUri(authority, parentId)
                    Log.d(TAG, "Built parent URI: $parentUri")
                    parentUri
                } else {
                    // Try to get tree URI if available
                    // For some providers, we might need to use the tree URI directly
                    Log.w(TAG, "Document is at root, trying alternative methods")
                    
                    // Try to extract tree URI from the document URI
                    // Some providers store the tree root in the document ID
                    val authority = uri.authority
                    // Build a tree URI - this might not work for all providers
                    // but it's worth trying
                    try {
                        DocumentsContract.buildTreeDocumentUri(authority, parentId.takeIf { it.isNotEmpty() } ?: documentId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not build tree URI, returning null", e)
                        null
                    }
                }
            } else {
                // For file URIs, get parent
                val path = uri.path ?: return null
                Log.d(TAG, "File path: $path")
                val parentPath = path.substringBeforeLast("/")
                Log.d(TAG, "Parent path: $parentPath")
                
                if (parentPath.isNotEmpty()) {
                    val parentUri = uri.buildUpon().path(parentPath).build()
                    Log.d(TAG, "Built parent URI from file path: $parentUri")
                    parentUri
                } else {
                    Log.w(TAG, "File is at root")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting parent URI", e)
            null
        }
    }

    private fun findFileInDirectory(
        context: Context,
        directoryUri: Uri,
        fileName: String
    ): Uri? {
        return try {
            Log.d(TAG, "Searching for file '$fileName' in directory: $directoryUri")
            
            // First, try using DocumentsContract API (most reliable for document URIs)
            if (DocumentsContract.isDocumentUri(context, directoryUri)) {
                try {
                    val documentId = DocumentsContract.getDocumentId(directoryUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUri(
                        directoryUri.authority ?: return null,
                        documentId
                    )
                    
                    Log.d(TAG, "Querying children URI: $childrenUri")
                    
                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        
                        val foundFiles = mutableListOf<String>()
                        while (cursor.moveToNext()) {
                            if (nameColumn >= 0) {
                                val childName = cursor.getString(nameColumn)
                                foundFiles.add(childName)
                                
                                // Case-insensitive comparison
                                if (childName.equals(fileName, ignoreCase = true)) {
                                    if (idColumn >= 0) {
                                        val childDocumentId = cursor.getString(idColumn)
                                        val authority = directoryUri.authority ?: return null
                                        val foundUri = DocumentsContract.buildDocumentUri(authority, childDocumentId)
                                        Log.d(TAG, "Found file via DocumentsContract: $foundUri")
                                        return foundUri
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Files found in directory: $foundFiles")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying DocumentsContract, trying DocumentFile fallback", e)
                }
            }
            
            // Fallback: Try using DocumentFile
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri)
                    ?: DocumentFile.fromSingleUri(context, directoryUri)
                
                if (directory != null && directory.isDirectory) {
                    Log.d(TAG, "Using DocumentFile to list directory")
                    val files = directory.listFiles()
                    val foundFiles = files?.map { it.name } ?: emptyList()
                    Log.d(TAG, "Files found via DocumentFile: $foundFiles")
                    
                    files?.forEach { file ->
                        Log.d(TAG, "Checking file: ${file.name} (looking for: $fileName)")
                    }
                    
                    // Case-insensitive comparison
                    val foundFile = files?.firstOrNull { 
                        it.name?.equals(fileName, ignoreCase = true) == true 
                    }
                    
                    if (foundFile != null) {
                        Log.d(TAG, "Found file via DocumentFile: ${foundFile.uri}")
                        return foundFile.uri
                    }
                } else {
                    Log.w(TAG, "DocumentFile is null or not a directory")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error using DocumentFile", e)
            }
            
            Log.w(TAG, "Could not find file '$fileName' in directory")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding file in directory", e)
            null
        }
    }

    /**
     * Tries to find an XML file in Downloads using MediaStore API.
     * This works around permission issues with DocumentsContract for Downloads provider.
     */
    private fun findFileInDownloadsUsingMediaStore(
        context: Context,
        fileName: String
    ): Uri? {
        return try {
            val contentResolver = context.contentResolver
            Log.d(TAG, "Querying MediaStore for file: $fileName")
            
            // MediaStore.Downloads is only available on API 29+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaStoreUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                Log.d(TAG, "Using MediaStore.Downloads (API 29+)")
                
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME
                )
                
                // Try exact match first
                var selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                var selectionArgs = arrayOf(fileName)
                
                contentResolver.query(
                    mediaStoreUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "MediaStore.Downloads query returned ${cursor.count} results")
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        Log.d(TAG, "Found file in MediaStore: $name")
                        if (name.equals(fileName, ignoreCase = true)) {
                            val id = cursor.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(mediaStoreUri, id)
                            Log.d(TAG, "Found matching XML file via MediaStore.Downloads: $uri")
                            return uri
                        }
                    }
                }
                
                // If exact match didn't work, try case-insensitive search by querying all and filtering
                Log.d(TAG, "Exact match failed, trying broader search")
                contentResolver.query(
                    mediaStoreUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "Broad MediaStore.Downloads query returned ${cursor.count} results")
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        if (name?.equals(fileName, ignoreCase = true) == true) {
                            val id = cursor.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(mediaStoreUri, id)
                            Log.d(TAG, "Found matching XML file via broad MediaStore.Downloads search: $uri")
                            return uri
                        }
                    }
                }
            } else {
                // For older Android versions, try using MediaStore.Files
                Log.d(TAG, "Using MediaStore.Files (API < 29)")
                val mediaStoreUri = MediaStore.Files.getContentUri("external")
                
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATA
                )
                
                // Try with media type filter
                var selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                var selectionArgs = arrayOf(fileName, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString())
                
                contentResolver.query(
                    mediaStoreUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "MediaStore.Files query returned ${cursor.count} results")
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        if (name.equals(fileName, ignoreCase = true)) {
                            val id = cursor.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(mediaStoreUri, id)
                            Log.d(TAG, "Found matching XML file via MediaStore.Files: $uri")
                            return uri
                        }
                    }
                }
                
                // Try without media type filter
                selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                selectionArgs = arrayOf(fileName)
                
                contentResolver.query(
                    mediaStoreUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "MediaStore.Files query (no media type filter) returned ${cursor.count} results")
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        if (name.equals(fileName, ignoreCase = true)) {
                            val id = cursor.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(mediaStoreUri, id)
                            Log.d(TAG, "Found matching XML file via MediaStore.Files (no filter): $uri")
                            return uri
                        }
                    }
                }
            }
            
            Log.w(TAG, "MediaStore query did not find file: $fileName")
            
            // Last resort: Try querying ALL files in Downloads to see what's available
            // This helps with debugging and might find the file if it wasn't indexed properly
            try {
                val debugUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                
                val debugProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(MediaStore.Downloads.DISPLAY_NAME)
                } else {
                    arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
                }
                
                context.contentResolver.query(
                    debugUri,
                    debugProjection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    }
                    
                    val foundFiles = mutableListOf<String>()
                    var count = 0
                    while (cursor.moveToNext() && count < 20) { // Limit to first 20 for logging
                        val name = cursor.getString(nameColumn)
                        foundFiles.add(name)
                        count++
                    }
                    Log.d(TAG, "Sample files in MediaStore Downloads (first 20): $foundFiles")
                    Log.d(TAG, "Total files in MediaStore Downloads: ${cursor.count}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in debug MediaStore query", e)
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for Downloads", e)
            null
        }
    }

    /**
     * Tries to find XML file by attempting to construct its URI based on the MIDI file's document ID.
     * This is a fallback for Downloads provider when we can't access the parent directory.
     */
    private fun tryFindXmlByDocumentId(
        context: Context,
        midiFileUri: Uri,
        xmlFileName: String
    ): Uri? {
        return try {
            if (!DocumentsContract.isDocumentUri(context, midiFileUri)) {
                return null
            }
            
            val midiDocumentId = DocumentsContract.getDocumentId(midiFileUri)
            Log.d(TAG, "MIDI document ID: $midiDocumentId")
            
            val authority = midiFileUri.authority ?: return null
            
            // For Downloads provider, try to find the XML file by querying MediaStore with a broader search
            // that includes the Downloads folder path
            try {
                // Try querying MediaStore.Files with Downloads folder filter
                val mediaStoreUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                
                val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(
                        MediaStore.Downloads._ID,
                        MediaStore.Downloads.DISPLAY_NAME,
                        MediaStore.Downloads.RELATIVE_PATH
                    )
                } else {
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DATA
                    )
                }
                
                // Query for files with matching name, case-insensitive
                val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                } else {
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                }
                val selectionArgs = arrayOf("%$xmlFileName%")
                
                context.contentResolver.query(
                    mediaStoreUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "Alternative MediaStore query returned ${cursor.count} results")
                    val idColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    }
                    val nameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    }
                    
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        Log.d(TAG, "Checking file: $name")
                        if (name.equals(xmlFileName, ignoreCase = true)) {
                            val id = cursor.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(mediaStoreUri, id)
                            Log.d(TAG, "Found XML file via alternative MediaStore query: $uri")
                            return uri
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in alternative MediaStore query", e)
            }
            
            // Last resort: Try to construct URI directly if we can extract a pattern
            // For Downloads, document IDs are often sequential or follow a pattern
            // But this is unreliable, so we'll skip it
            
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error trying to find XML by document ID", e)
            null
        }
    }
}
