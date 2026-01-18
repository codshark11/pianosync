package io.pianosync.midi.ui.screens.sheetmusic

import android.util.Log
import java.io.File

import android.content.Context
import android.content.res.AssetManager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel

import io.pianosync.midi.lib.toolkit

class SheetMusicViewModel : ViewModel() {

    // Native wrapper instance
    private var initialized = false
    private lateinit var resourcePath: String
    private lateinit var toolkit: toolkit

    var svgString = mutableStateOf("<svg></svg>")
    val fontOptions = listOf("Leipzig", "Bravura", "Leland", "Petaluma")

    private var viewSize by mutableStateOf(IntSize.Zero)
    private var currentPage by mutableStateOf(1)
    private var scaleIndex by mutableStateOf(3)
    private val scaleValues = listOf(50, 60, 80, 100, 150, 200)
    private var selectedFont = "Leipzig"

    fun initIfNeeded(context: Context) {
        if (initialized) return
        initialized = true

        // Copy assets
        val targetDir = File(context.filesDir, "verovio/data")
        resourcePath = "${context.filesDir.absolutePath}/verovio/data"
        copyAssetFolder(context.assets, "verovio/data", targetDir)

        // Init toolkit
        toolkit = toolkit(false)
        toolkit.setResourcePath(resourcePath)
        toolkit.setOptions("{'svgViewBox': 'true'}")
        toolkit.setOptions("{'scaleToPageSize': 'true'}")
        toolkit.setOptions("{'adjustPageHeight': 'true'}")
        toolkit.setOptions("{'fontTextLiberation': 'true'}")
    }

    override fun onCleared() {
        super.onCleared()
        if (::toolkit.isInitialized) {
            toolkit.delete()
        }
    }

    fun canPrevious(): Boolean { 
        return if (::toolkit.isInitialized) (currentPage > 1) else false 
    }
    
    fun canNext(): Boolean { 
        return if (::toolkit.isInitialized) (currentPage < toolkit.getPageCount()) else false 
    }
    
    fun canZoomOut(): Boolean { return scaleIndex > 0 }
    fun canZoomIn(): Boolean { return scaleIndex < scaleValues.size - 1 }
    fun getVersion(): String { 
        return if (::toolkit.isInitialized) toolkit.getVersion() else "Unknown"
    }

    fun onPrevious() {
        if (canPrevious()) {
            currentPage--
            updateSvg()
        }
    }

    fun onNext() {
        if (canNext()) {
            currentPage++
            updateSvg()
        }
    }

    fun onZoomOut() {
        if (canZoomOut()) {
            scaleIndex--
            applyZoom()
        }
    }

    fun onZoomIn() {
        if (canZoomIn()) {
            scaleIndex++
            applyZoom()
        }
    }

    fun onFontSelect(font: String) {
        if (selectedFont != font && ::toolkit.isInitialized) {
            selectedFont = font
            applyFont()
        }
    }

    fun onSize(size: IntSize) {
        if (viewSize == size) return
        viewSize = size
        if (::toolkit.isInitialized) {
            applySize()
        }
    }

    fun onLoadFile(filename: String): Boolean {
        if (!::toolkit.isInitialized) {
            Log.e("SheetMusicViewModel", "Toolkit not initialized when trying to load file: $filename")
            return false
        }
        
        val file = File(filename)
        if (!file.exists()) {
            Log.e("SheetMusicViewModel", "File does not exist: $filename")
            return false
        }
        
        Log.d("SheetMusicViewModel", "Loading file: $filename (exists: ${file.exists()}, size: ${file.length()} bytes)")
        
        // Clear any previous log messages
        toolkit.getLog()
        
        val success = toolkit.loadFile(filename)
        
        if (!success) {
            val errorLog = toolkit.getLog()
            Log.e("SheetMusicViewModel", "Failed to load file: $filename")
            Log.e("SheetMusicViewModel", "Verovio error log: $errorLog")
            
            // Try to read first few lines of the file to see what format it is
            try {
                val firstLines = file.readLines().take(10).joinToString("\n")
                Log.d("SheetMusicViewModel", "First 10 lines of file:\n$firstLines")
            } catch (e: Exception) {
                Log.e("SheetMusicViewModel", "Error reading file contents", e)
            }
        } else {
            val pageCount = toolkit.getPageCount()
            Log.d("SheetMusicViewModel", "Successfully loaded file: $filename, pages: $pageCount")
            currentPage = 1
            updateSvg()
        }
        
        return success
    }

    fun onLoadData(data: String): Boolean {
        return if (::toolkit.isInitialized) {
            toolkit.loadData(data)
            currentPage = 1
            updateSvg()
            true
        } else {
            false
        }
    }

    fun getPageCount(): Int {
        return if (::toolkit.isInitialized) toolkit.getPageCount() else 0
    }

    private fun applyFont() {
        if (!::toolkit.isInitialized) return
        val scaleOptionsJSON = """{"font": "$selectedFont"}"""
        toolkit.setOptions(scaleOptionsJSON)
        toolkit.redoLayout()
        if (toolkit.getPageCount() < currentPage) {
            currentPage = toolkit.getPageCount()
        }
        updateSvg()
    }

    private fun applySize() {
        if (!::toolkit.isInitialized) return
        val height = 2100f * viewSize.height / viewSize.width
        val sizeJSON = """{"pageHeight": $height}"""
        toolkit.setOptions(sizeJSON)
        applyZoom()
    }

    private fun applyZoom() {
        if (!::toolkit.isInitialized) return
        val scaleOptionsJSON = """{"scale": ${scaleValues[scaleIndex]}}"""
        toolkit.setOptions(scaleOptionsJSON)
        toolkit.redoLayout()
        if (toolkit.getPageCount() < currentPage) {
            currentPage = toolkit.getPageCount()
        }
        updateSvg()
    }

    private fun updateSvg() {
        if (::toolkit.isInitialized) {
            svgString.value = toolkit.renderToSVG(currentPage)
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: File) {
        val files = assetManager.list(fromAssetPath) ?: return
        if (!toPath.exists()) toPath.mkdirs()
        for (filename in files) {
            val assetPath = "$fromAssetPath/$filename"
            val outFile = File(toPath, filename)
            if (assetManager.list(assetPath)?.isNotEmpty() == true) {
                // It's a folder
                copyAssetFolder(assetManager, assetPath, outFile)
            } else {
                assetManager.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
