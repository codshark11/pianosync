package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

/**
 * Represents a white key note (non-sharp, non-flat note).
 * White notes consist of a letter (A through G) and an octave (0 through 10).
 * The octave changes from G to A. After G2 comes A3. Middle-C is C4.
 */
data class WhiteNote(
    val letter: Int,  // 0=A, 1=B, 2=C, 3=D, 4=E, 5=F, 6=G
    val octave: Int   // 0 through 10
) {
    companion object {
        const val A = 0
        const val B = 1
        const val C = 2
        const val D = 3
        const val E = 4
        const val F = 5
        const val G = 6

        val TopTreble = WhiteNote(E, 5)
        val BottomTreble = WhiteNote(F, 4)
        val TopBass = WhiteNote(G, 3)
        val BottomBass = WhiteNote(A, 3)
        val MiddleC = WhiteNote(C, 4)

        /**
         * Convert MIDI note number to WhiteNote
         * Matching MidiSheetMusic-Android KeySignature.GetWhiteNote()
         * MIDI note 60 = C4 (Middle C)
         * 
         * Uses NoteScale.FromNumber = (notenumber + 3) % 12
         * Octave = (notenumber + 3) / 12 - 1
         * Maps notescale to letter using whole_sharps array (for C major key)
         */
        fun fromMidiNote(midiNote: Int): WhiteNote {
            // Matching NoteScale.FromNumber: (number + 3) % 12
            val notescale = (midiNote + 3) % 12
            // Matching KeySignature.GetWhiteNote: octave = (notenumber + 3) / 12 - 1
            val octave = (midiNote + 3) / 12 - 1
            
            // Matching KeySignature.GetWhiteNote: whole_sharps array
            // whole_sharps = [A, A, B, C, C, D, D, E, F, F, G, G] for notescale 0-11
            val whole_sharps = intArrayOf(
                A, A,  // 0=A, 1=A# -> A
                B,     // 2=B
                C, C,  // 3=C, 4=C# -> C
                D, D,  // 5=D, 6=D# -> D
                E,     // 7=E
                F, F,  // 8=F, 9=F# -> F
                G, G   // 10=G, 11=G# -> G
            )
            
            val letter = whole_sharps[notescale]
            
            return WhiteNote(letter, octave)
        }
        
        /**
         * Return the top note in the staff of the given clef.
         * Matching MidiSheetMusic-Android WhiteNote.Top()
         */
        fun Top(clef: Clef): WhiteNote {
            return if (clef == Clef.Treble) TopTreble else TopBass
        }
        
        /**
         * Return the bottom note in the staff of the given clef.
         * Matching MidiSheetMusic-Android WhiteNote.Bottom()
         */
        fun Bottom(clef: Clef): WhiteNote {
            return if (clef == Clef.Treble) BottomTreble else BottomBass
        }
    }

    /**
     * Return the distance (in white notes) between this note and another note.
     * For example, C4 - A4 = 2
     */
    fun dist(other: WhiteNote): Int {
        return (octave - other.octave) * 7 + (letter - other.letter)
    }

    /**
     * Return this note plus the given amount (in white notes).
     * The amount may be positive or negative.
     */
    fun add(amount: Int): WhiteNote {
        val num = octave * 7 + letter
        val newNum = (num + amount).coerceAtLeast(0)
        return WhiteNote(newNum % 7, newNum / 7)
    }

    /**
     * Convert this white note to a MIDI note number (assuming natural note, no sharps/flats)
     */
    fun toMidiNote(): Int {
        val baseNote = when (letter) {
            A -> 9   // A
            B -> 11  // B
            C -> 0   // C
            D -> 2   // D
            E -> 4   // E
            F -> 5   // F
            G -> 7   // G
            else -> 0
        }
        return (octave + 1) * 12 + baseNote
    }
}
