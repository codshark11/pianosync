package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import io.pianosync.midi.ui.screens.player.MidiNote

/**
 * Determines which clef (Treble or Bass) to use for each measure.
 * Matching MidiSheetMusic-Android ClefMeasures.
 *
 * - MainClef: from average MIDI of all notes (>= Middle C → Treble, else Bass).
 * - Per measure: average MIDI in that measure.
 *   - >= BottomTreble (F4 = 65) → Treble
 *   - <= TopBass (G3 = 55) → Bass
 *   - between → use MainClef
 */
class ClefMeasures(
    private val notes: List<MidiNote>,
    private val measureLengthMs: Long
) {
    /** MIDI: Middle C=60, BottomTreble F4=65, TopBass G3=55. */
    private val middleC = 60
    private val bottomTrebleMidi = 65   // F4
    private val topBassMidi = 55        // G3

    private val mainClef: Clef = mainClef(notes)
    private val clefs: List<Clef> = buildClefs()

    private fun mainClef(notes: List<MidiNote>): Clef {
        if (notes.isEmpty()) return Clef.Treble
        val avg = notes.map { it.note }.average()
        return if (avg >= middleC) Clef.Treble else Clef.Bass
    }

    private fun buildClefs(): List<Clef> {
        if (notes.isEmpty() || measureLengthMs <= 0) return listOf(mainClef)
        val sorted = notes.sortedBy { it.startTime }
        val result = mutableListOf<Clef>()
        var nextMeasure = measureLengthMs
        var clef = mainClef
        var pos = 0

        while (pos < sorted.size) {
            var sum = 0
            var count = 0
            while (pos < sorted.size && sorted[pos].startTime < nextMeasure) {
                sum += sorted[pos].note
                count++
                pos++
            }
            if (count == 0) count = 1
            val avgnote = sum / count
            when {
                avgnote == 0 -> { /* keep prev */ }
                avgnote >= bottomTrebleMidi -> clef = Clef.Treble
                avgnote <= topBassMidi -> clef = Clef.Bass
                else -> clef = mainClef
            }
            result.add(clef)
            nextMeasure += measureLengthMs
        }
        result.add(clef)
        return result
    }

    /** Clef for the measure containing startTimeMs. */
    fun getClef(startTimeMs: Long): Clef {
        if (clefs.isEmpty()) return mainClef
        val measureIndex = (startTimeMs / measureLengthMs).toInt().coerceAtLeast(0)
        return if (measureIndex >= clefs.size) clefs.last() else clefs[measureIndex]
    }
}
