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

/** @class MidiEvent
 * A MidiEvent represents a single event (such as EventNoteOn) in the
 * Midi file. It includes the delta time of the event.
 */
class MidiEvent
/** The raw byte value, for Sysex and meta events  */
    : Comparator<MidiEvent?>, Comparable<MidiEvent> {
    var DeltaTime: Int = 0

    /** The time between the previous event and this on  */
    var StartTime: Int = 0

    /** The absolute time this event occurs  */
    var HasEventflag: Boolean = false

    /** False if this is using the previous eventflag  */
    var EventFlag: Byte = 0

    /** NoteOn, NoteOff, etc.  Full list is in class MidiFile  */
    var Channel: Byte = 0

    /** The channel this event occurs on  */
    var Notenumber: Byte = 0

    /** The note number   */
    var Velocity: Byte = 0

    /** The volume of the note  */
    var Instrument: Byte = 0

    /** The instrument  */
    var KeyPressure: Byte = 0

    /** The key pressure  */
    var ChanPressure: Byte = 0

    /** The channel pressure  */
    var ControlNum: Byte = 0

    /** The controller number  */
    var ControlValue: Byte = 0

    /** The controller value  */
    var PitchBend: Short = 0

    /** The pitch bend value  */
    var Numerator: Byte = 0

    /** The numerator, for TimeSignature meta events  */
    var Denominator: Byte = 0

    /** The denominator, for TimeSignature meta events  */
    var Tempo: Int = 0

    /** The tempo, for Tempo meta events  */
    var Metaevent: Byte = 0

    /** The metaevent, used if eventflag is MetaEvent  */
    var Metalength: Int = 0

    /** The metaevent length   */
    var Value: ByteArray? = null

    /** Return a copy of this event  */
    fun Clone(): MidiEvent {
        val mevent = MidiEvent()
        mevent.DeltaTime = DeltaTime
        mevent.StartTime = StartTime
        mevent.HasEventflag = HasEventflag
        mevent.EventFlag = EventFlag
        mevent.Channel = Channel
        mevent.Notenumber = Notenumber
        mevent.Velocity = Velocity
        mevent.Instrument = Instrument
        mevent.KeyPressure = KeyPressure
        mevent.ChanPressure = ChanPressure
        mevent.ControlNum = ControlNum
        mevent.ControlValue = ControlValue
        mevent.PitchBend = PitchBend
        mevent.Numerator = Numerator
        mevent.Denominator = Denominator
        mevent.Tempo = Tempo
        mevent.Metaevent = Metaevent
        mevent.Metalength = Metalength
        mevent.Value = Value
        return mevent
    }

    /** Compare two MidiEvents based on their start times (for Comparator).  */
    override fun compare(x: MidiEvent?, y: MidiEvent?): Int {
        if (x == null && y == null) return 0
        if (x == null) return -1
        if (y == null) return 1
        
        if (x.StartTime == y.StartTime) {
            if (x.EventFlag == y.EventFlag) {
                return x.Notenumber - y.Notenumber
            } else {
                return x.EventFlag - y.EventFlag
            }
        } else {
            return x.StartTime - y.StartTime
        }
    }

    /** Compare this MidiEvent with another (for Comparable).  */
    override fun compareTo(other: MidiEvent): Int {
        return compare(this, other)
    }
}
