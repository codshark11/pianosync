package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import io.pianosync.midi.R
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.Clef

/**
 * A ClefSymbol represents either a Treble or Bass Clef image.
 * The clef can be either normal or small size. Normal size is
 * used at the beginning of a new staff, on the left side. The
 * small symbols are used to show clef changes within a staff.
 */
class ClefSymbol(
    private val clef: Clef,
    private val startTime: Long,
    private val smallSize: Boolean = false
) : MusicSymbol {
    
    companion object {
        // Constants from SheetMusic - scaled up for visibility
        private const val ScaleFactor = 2.5f
        private const val BaseLineSpace = 7f
        private const val BaseLineWidth = 1f
        private const val LineSpace = BaseLineSpace * ScaleFactor
        private const val LineWidth = BaseLineWidth * ScaleFactor
        private const val NoteWidth = 3f * LineSpace / 2f
        private const val NoteHeight = LineSpace + LineWidth
        private const val StaffHeight = LineSpace * 4f + LineWidth * 5f
        
        private var trebleBitmap: android.graphics.Bitmap? = null
        private var bassBitmap: android.graphics.Bitmap? = null
        
        /**
         * Load the Treble/Bass clef images into memory.
         */
        fun loadImages(context: Context) {
            if (trebleBitmap == null || bassBitmap == null) {
                val res = context.resources
                trebleBitmap = BitmapFactory.decodeResource(res, R.drawable.treble)
                bassBitmap = BitmapFactory.decodeResource(res, R.drawable.bass)
            }
        }
    }
    
    private var width: Float = getMinWidth()
    
    override fun getStartTime(): Long = startTime
    
    override fun getMinWidth(): Float {
        return if (smallSize) {
            NoteWidth * 2f
        } else {
            NoteWidth * 3f
        }
    }
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float {
        return if (clef == Clef.Treble && !smallSize) {
            NoteHeight * 2f
        } else {
            0f
        }
    }
    
    override fun getBelowStaff(): Float {
        return when {
            clef == Clef.Treble && !smallSize -> NoteHeight * 2f
            clef == Clef.Treble && smallSize -> NoteHeight * 1f
            else -> 0f
        }
    }
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        // Use native canvas for bitmap drawing
        val image = when (clef) {
            Clef.Treble -> trebleBitmap
            Clef.Bass -> bassBitmap
        } ?: return
        
        val height: Float
        val y: Float
        
        when (clef) {
            Clef.Treble -> {
                if (smallSize) {
                    height = StaffHeight + StaffHeight / 4f
                    y = ytop
                } else {
                    height = 3f * StaffHeight / 2f + NoteHeight / 2f
                    y = ytop - NoteHeight
                }
            }
            Clef.Bass -> {
                if (smallSize) {
                    height = StaffHeight - 3f * NoteHeight / 2f
                    y = ytop
                } else {
                    height = StaffHeight - NoteHeight
                    y = ytop
                }
            }
        }
        
        // Scale the image width to match the height
        val imgWidth = image.width * height / image.height
        val srcRect = android.graphics.Rect(0, 0, image.width, image.height)
        val destRect = android.graphics.Rect(0, y.toInt(), imgWidth.toInt(), (y + height).toInt())
        
        val androidCanvas = drawScope.drawContext.canvas.nativeCanvas
        // Translate to align symbol (right-align for clef)
        androidCanvas.save()
        androidCanvas.translate(getWidth() - getMinWidth(), 0f)
        
        val paint = android.graphics.Paint()
        androidCanvas.drawBitmap(image, srcRect, destRect, paint)
        androidCanvas.restore()
    }
    
    fun getClef(): Clef = clef
    
    override fun toString(): String {
        return "ClefSymbol clef=$clef small=$smallSize width=$width"
    }
}
