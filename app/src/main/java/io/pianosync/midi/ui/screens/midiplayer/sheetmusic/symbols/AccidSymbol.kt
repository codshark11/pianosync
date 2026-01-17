package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.Clef
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.WhiteNote

/**
 * An accidental symbol (sharp, flat, or natural) at a specific staff position.
 * Matching MidiSheetMusic-Android AccidSymbol.
 */
class AccidSymbol(
    private val accid: Accid,
    private val whitenote: WhiteNote,
    private val clef: Clef
) : MusicSymbol {

    companion object {
        private const val ScaleFactor = 2.5f
        private const val LineSpace = 7f * ScaleFactor
        private const val LineWidth = 1f * ScaleFactor
        private const val NoteHeight = LineSpace + LineWidth
        private const val NoteWidth = 3f * LineSpace / 2f
    }

    private var width: Float = getMinWidth()

    /** White note this accidental is displayed at (for overlap detection). */
    fun getNote(): WhiteNote = whitenote

    override fun getStartTime(): Long = -1L

    override fun getMinWidth(): Float = 3f * NoteHeight / 2f

    override fun getWidth(): Float = width

    override fun setWidth(value: Float) {
        width = value
    }

    override fun getAboveStaff(): Float {
        // Matching MidiSheetMusic-Android AccidSymbol.getAboveStaff()
        val topStaff = WhiteNote.Top(clef)
        var dist = topStaff.dist(whitenote) * NoteHeight / 2
        when (accid) {
            Accid.Sharp, Accid.Natural -> dist -= NoteHeight
            Accid.Flat -> dist -= 3 * NoteHeight / 2
            Accid.None -> {}
        }
        return if (dist < 0) -dist else 0f
    }

    override fun getBelowStaff(): Float {
        // Matching MidiSheetMusic-Android AccidSymbol.getBelowStaff()
        val bottomStaff = WhiteNote.Bottom(clef)
        var dist = bottomStaff.dist(whitenote) * NoteHeight / 2 + NoteHeight
        when (accid) {
            Accid.Sharp, Accid.Natural -> dist += NoteHeight
            Accid.Flat, Accid.None -> {}
        }
        return if (dist > 0) dist else 0f
    }

    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        drawScope.withTransform({
            translate(getWidth() - getMinWidth(), 0f)
        }) {
            // Matching MidiSheetMusic-Android: ynote = ytop + WhiteNote.Top(clef).Dist(whitenote) * NoteHeight/2
            val topStaff = WhiteNote.Top(clef)
            val ynote = ytop + topStaff.dist(whitenote) * NoteHeight / 2
            when (accid) {
                Accid.Sharp -> drawSharp(drawScope, color, ynote)
                Accid.Flat -> drawFlat(drawScope, color, ynote)
                Accid.Natural -> drawNatural(drawScope, color, ynote)
                Accid.None -> {}
            }
        }
    }

    /** Draw sharp symbol. Matching MidiSheetMusic-Android DrawSharp. */
    private fun drawSharp(drawScope: DrawScope, color: Color, ynote: Float) {
        var ystart = ynote - NoteHeight
        var yend = ynote + 2 * NoteHeight
        var x = NoteHeight / 2
        drawScope.drawLine(color, Offset(x, ystart + 2), Offset(x, yend), strokeWidth = 1f)
        x += NoteHeight / 2
        drawScope.drawLine(color, Offset(x, ystart), Offset(x, yend - 2), strokeWidth = 1f)

        val xstart = NoteHeight / 2 - NoteHeight / 4
        val xend = NoteHeight + NoteHeight / 4
        ystart = ynote + LineWidth
        yend = ystart - LineWidth - LineSpace / 4
        drawScope.drawLine(color, Offset(xstart, ystart), Offset(xend, yend), strokeWidth = LineSpace / 2)
        ystart += LineSpace
        yend += LineSpace
        drawScope.drawLine(color, Offset(xstart, ystart), Offset(xend, yend), strokeWidth = LineSpace / 2)
    }

    /** Draw flat symbol. Matching MidiSheetMusic-Android DrawFlat (3 bezier curves). */
    private fun drawFlat(drawScope: DrawScope, color: Color, ynote: Float) {
        val x = LineSpace / 4
        drawScope.drawLine(
            color,
            Offset(x, ynote - NoteHeight - NoteHeight / 2),
            Offset(x, ynote + NoteHeight),
            strokeWidth = 1f
        )

        val endY = ynote + LineSpace + LineWidth + 1
        val curves = listOf(
            floatArrayOf(x + LineSpace / 2, ynote - LineSpace / 2, x + LineSpace, ynote + LineSpace / 3, x, endY),
            floatArrayOf(x + LineSpace / 2, ynote - LineSpace / 2, x + LineSpace + LineSpace / 4, ynote + LineSpace / 3 - LineSpace / 4, x, endY),
            floatArrayOf(x + LineSpace / 2, ynote - LineSpace / 2, x + LineSpace + LineSpace / 2, ynote + LineSpace / 3 - LineSpace / 2, x, endY)
        )
        curves.forEach { c ->
            val path = Path().apply {
                moveTo(x, ynote + LineSpace / 4)
                cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
            }
            drawScope.drawPath(path, color, style = Stroke(width = 1f))
        }
    }

    /** Draw natural symbol. Matching MidiSheetMusic-Android DrawNatural. */
    private fun drawNatural(drawScope: DrawScope, color: Color, ynote: Float) {
        var ystart = ynote - LineSpace - LineWidth
        var yend = ynote + LineSpace + LineWidth
        var x = LineSpace / 2
        drawScope.drawLine(color, Offset(x, ystart), Offset(x, yend), strokeWidth = 1f)
        x += LineSpace - LineSpace / 4
        ystart = ynote - LineSpace / 4
        yend = ynote + 2 * LineSpace + LineWidth - LineSpace / 4
        drawScope.drawLine(color, Offset(x, ystart), Offset(x, yend), strokeWidth = 1f)

        val xstart = LineSpace / 2
        val xend = xstart + LineSpace - LineSpace / 4
        ystart = ynote + LineWidth
        yend = ystart - LineWidth - LineSpace / 4
        drawScope.drawLine(color, Offset(xstart, ystart), Offset(xend, yend), strokeWidth = LineSpace / 2)
        ystart += LineSpace
        yend += LineSpace
        drawScope.drawLine(color, Offset(xstart, ystart), Offset(xend, yend), strokeWidth = LineSpace / 2)
    }

    override fun toString(): String = "AccidSymbol accid=$accid whitenote=$whitenote clef=$clef width=$width"
}
