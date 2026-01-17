package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import io.pianosync.midi.R

/**
 * A TimeSigSymbol represents the time signature at the beginning
 * of the staff. We use pre-made images for the numbers, instead of
 * drawing strings.
 */
class TimeSigSymbol(
    private val numerator: Int,
    private val denominator: Int
) : MusicSymbol {
    
    companion object {
        // Constants from SheetMusic - scaled up for visibility
        private const val ScaleFactor = 2.5f
        private const val LineSpace = 7f * ScaleFactor
        private const val LineWidth = 1f * ScaleFactor
        private const val NoteHeight = LineSpace + LineWidth
        
        private var images: Array<android.graphics.Bitmap?>? = null
        
        /**
         * Load the time signature number images into memory.
         */
        fun loadImages(context: Context) {
            if (images != null) {
                return
            }
            images = arrayOfNulls(13)
            val res = context.resources
            images!![2] = BitmapFactory.decodeResource(res, R.drawable.two)
            images!![3] = BitmapFactory.decodeResource(res, R.drawable.three)
            images!![4] = BitmapFactory.decodeResource(res, R.drawable.four)
            images!![6] = BitmapFactory.decodeResource(res, R.drawable.six)
            images!![8] = BitmapFactory.decodeResource(res, R.drawable.eight)
            images!![9] = BitmapFactory.decodeResource(res, R.drawable.nine)
            images!![12] = BitmapFactory.decodeResource(res, R.drawable.twelve)
        }
    }
    
    private var width: Float = getMinWidth()
    
    /**
     * Check if we can draw this time signature (images are loaded and valid)
     */
    private fun canDraw(): Boolean {
        if (images == null) return false
        return numerator >= 0 && 
               numerator < images!!.size && 
               images!![numerator] != null &&
               denominator >= 0 && 
               denominator < images!!.size && 
               images!![denominator] != null
    }
    
    override fun getStartTime(): Long = -1L
    
    override fun getMinWidth(): Float {
        if (!canDraw() || images == null) {
            return 0f
        }
        val twoImage = images!![2] ?: return 0f
        return twoImage.width * NoteHeight * 2f / twoImage.height
    }
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float = 0f
    
    override fun getBelowStaff(): Float = 0f
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        if (!canDraw() || images == null) {
            return
        }
        
        val numerImage = images!![numerator] ?: return
        val denomImage = images!![denominator] ?: return
        
        // Scale the image width to match the height
        val imgHeight = NoteHeight * 2f
        val imgWidth = numerImage.width * imgHeight / numerImage.height
        
        // Position time signature numbers on the staff
        // ytop is the Y position of the top staff line (line 4)
        // Numerator should be centered around middle line (line 2): ytop + 2*LineSpace
        // Denominator should be centered around line 3: ytop + 3*LineSpace
        val numeratorY = ytop + 2f * LineSpace - imgHeight / 2f
        val denominatorY = ytop + 3f * LineSpace - imgHeight / 2f
        
        // Draw numerator
        val srcRect = android.graphics.Rect(0, 0, numerImage.width, numerImage.height)
        val numerDestRect = android.graphics.Rect(
            0, 
            numeratorY.toInt(), 
            imgWidth.toInt(), 
            (numeratorY + imgHeight).toInt()
        )
        
        val androidCanvas = drawScope.drawContext.canvas.nativeCanvas
        androidCanvas.save()
        androidCanvas.translate(getWidth() - getMinWidth(), 0f)
        
        val paint = android.graphics.Paint()
        androidCanvas.drawBitmap(numerImage, srcRect, numerDestRect, paint)
        
        // Draw denominator
        val denomDestRect = android.graphics.Rect(
            0, 
            denominatorY.toInt(), 
            imgWidth.toInt(), 
            (denominatorY + imgHeight).toInt()
        )
        androidCanvas.drawBitmap(denomImage, srcRect, denomDestRect, paint)
        
        androidCanvas.restore()
    }
    
    override fun toString(): String {
        return "TimeSigSymbol numerator=$numerator denominator=$denominator"
    }
}
