/*
 * Copyright (c) 2007-2011 Madhav Vaidyanathan
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */
package io.pianosync.midi.data.parser.midi

/** @class MidiOptions
 * The MidiOptions class contains the available options for
 * modifying the midi file and the sheet music.
 */
data class MidiOptions(
    /** The tracks to display/include */
    var tracks: BooleanArray = BooleanArray(0),
    
    /** Which tracks to mute */
    var mute: BooleanArray = BooleanArray(0),
    
    /** The instruments to use per track */
    var instruments: IntArray = IntArray(0),
    
    /** Whether to use the default instruments */
    var useDefaultInstruments: Boolean = true,
    
    /** The time signature to use */
    var time: TimeSignature? = null,
    
    /** The tempo (microseconds per quarter note) */
    var tempo: Int = 0,
    
    /** The amount to transpose (shift) notes by */
    var transpose: Int = 0,
    
    /** The time to start playing from (in pulses) */
    var pauseTime: Int = 0,
    
    /** The time to shift notes by (in pulses) */
    var shifttime: Int = 0,
    
    /** Combine notes within this interval (milliseconds) */
    var combineInterval: Int = 40,
    
    /** Display as two staffs (treble and bass) */
    var twoStaffs: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MidiOptions

        if (!tracks.contentEquals(other.tracks)) return false
        if (!mute.contentEquals(other.mute)) return false
        if (!instruments.contentEquals(other.instruments)) return false
        if (useDefaultInstruments != other.useDefaultInstruments) return false
        if (time != other.time) return false
        if (tempo != other.tempo) return false
        if (transpose != other.transpose) return false
        if (pauseTime != other.pauseTime) return false
        if (shifttime != other.shifttime) return false
        if (combineInterval != other.combineInterval) return false
        if (twoStaffs != other.twoStaffs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tracks.contentHashCode()
        result = 31 * result + mute.contentHashCode()
        result = 31 * result + instruments.contentHashCode()
        result = 31 * result + useDefaultInstruments.hashCode()
        result = 31 * result + (time?.hashCode() ?: 0)
        result = 31 * result + tempo
        result = 31 * result + transpose
        result = 31 * result + pauseTime
        result = 31 * result + shifttime
        result = 31 * result + combineInterval
        result = 31 * result + twoStaffs.hashCode()
        return result
    }
}
