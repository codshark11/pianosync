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

import java.io.Serializable

/** @class TimeSignature
 * The TimeSignature class represents
 * - The time signature of the song, such as 4/4, 3/4, or 6/8 time, and
 * - The number of pulses per quarter note
 * - The number of microseconds per quarter note
 *
 * In midi files, all time is measured in "pulses".  Each note has
 * a start time (measured in pulses), and a duration (measured in
 * pulses).  This class is used mainly to convert pulse durations
 * (like 120, 240, etc) into note durations (half, quarter, eighth, etc).
 */
class TimeSignature(numerator: Int, denominator: Int, quarternote: Int, tempo: Int) : Serializable {
    /** Get the numerator of the time signature  */
    val numerator: Int
    /** Get the denominator of the time signature  */
    /** Numerator of the time signature  */
    val denominator: Int
    /** Get the number of pulses per quarter note  */
    /** Denominator of the time signature  */
    val quarter: Int
    /** Get the number of pulses per measure  */
    /** Number of pulses per quarter note  */
    val measure: Int
    /** Get the number of microseconds per quarter note  */
    /** Number of pulses per measure  */
    val tempo: Int

    /** Number of microseconds per quarter note  */

    /** Create a new time signature, with the given numerator,
     * denominator, pulses per quarter note, and tempo.
     */
    init {
        var numerator = numerator
        if (numerator <= 0 || denominator <= 0 || quarternote <= 0) {
            throw MidiFileException("Invalid time signature", 0)
        }

        /* Midi File gives wrong time signature sometimes */
        if (numerator == 5) {
            numerator = 4
        }

        this.numerator = numerator
        this.denominator = denominator
        this.quarter = quarternote
        this.tempo = tempo

        val beat: Int
        if (denominator < 4) beat = quarternote * 2
        else beat = quarternote / (denominator / 4)

        measure = numerator * beat
    }

    /** Return which measure the given time (in pulses) belongs to.  */
    fun GetMeasure(time: Int): Int {
        return time / measure
    }

    /** Given a duration in pulses, return the closest note duration.  */
    fun GetNoteDuration(duration: Int): NoteDuration {
        val whole = this.quarter * 4

        /**
         * 1       = 32/32
         * 3/4     = 24/32
         * 1/2     = 16/32
         * 3/8     = 12/32
         * 1/4     =  8/32
         * 3/16    =  6/32
         * 1/8     =  4/32 =    8/64
         * triplet         = 5.33/64
         * 1/16    =  2/32 =    4/64
         * 1/32    =  1/32 =    2/64
         */
        if (duration >= 28 * whole / 32) return NoteDuration.Whole
        else if (duration >= 20 * whole / 32) return NoteDuration.DottedHalf
        else if (duration >= 14 * whole / 32) return NoteDuration.Half
        else if (duration >= 10 * whole / 32) return NoteDuration.DottedQuarter
        else if (duration >= 7 * whole / 32) return NoteDuration.Quarter
        else if (duration >= 5 * whole / 32) return NoteDuration.DottedEighth
        else if (duration >= 6 * whole / 64) return NoteDuration.Eighth
        else if (duration >= 5 * whole / 64) return NoteDuration.Triplet
        else if (duration >= 3 * whole / 64) return NoteDuration.Sixteenth
        else return NoteDuration.ThirtySecond
    }

    /** Return the time period (in pulses) the the given duration spans  */
    fun DurationToTime(dur: NoteDuration): Int {
        val eighth = this.quarter / 2
        val sixteenth = eighth / 2

        when (dur) {
            NoteDuration.Whole -> return this.quarter * 4
            NoteDuration.DottedHalf -> return this.quarter * 3
            NoteDuration.Half -> return this.quarter * 2
            NoteDuration.DottedQuarter -> return 3 * eighth
            NoteDuration.Quarter -> return this.quarter
            NoteDuration.DottedEighth -> return 3 * sixteenth
            NoteDuration.Eighth -> return eighth
            NoteDuration.Triplet -> return this.quarter / 3
            NoteDuration.Sixteenth -> return sixteenth
            NoteDuration.ThirtySecond -> return sixteenth / 2
            else -> return 0
        }
    }

    override fun toString(): String {
        return String.format(
            "TimeSignature=%1\$s/%2\$s quarter=%3\$s tempo=%4\$s",
            numerator, denominator, this.quarter, tempo
        )
    }

    companion object {
        /** Convert a note duration into a stem duration.  Dotted durations
         * are converted into their non-dotted equivalents.
         */
        fun GetStemDuration(dur: NoteDuration?): NoteDuration? {
            if (dur == NoteDuration.DottedHalf) return NoteDuration.Half
            else if (dur == NoteDuration.DottedQuarter) return NoteDuration.Quarter
            else if (dur == NoteDuration.DottedEighth) return NoteDuration.Eighth
            else return dur
        }
    }
}
