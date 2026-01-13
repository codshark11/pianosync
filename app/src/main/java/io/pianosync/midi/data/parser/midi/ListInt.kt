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

import java.util.Arrays

/** @class ListInt
 * An ArrayList of int types.
 */
class ListInt {
    private var data: IntArray

    /** The list of ints  */
    private var count: Int

    /** The size of the list  */
    constructor() {
        data = IntArray(11)
        count = 0
    }

    constructor(capacity: Int) {
        data = IntArray(capacity)
        count = 0
    }

    fun size(): Int {
        return count
    }

    fun add(x: Int) {
        if (data.size == count) {
            val newdata = IntArray(count * 2)
            for (i in 0..<count) {
                newdata[i] = data[i]
            }
            data = newdata
        }
        data[count] = x
        count++
    }

    fun get(index: Int): Int {
        return data[index]
    }

    fun set(index: Int, x: Int) {
        data[index] = x
    }

    fun contains(x: Int): Boolean {
        for (i in 0..<count) {
            if (data[i] == x) {
                return true
            }
        }
        return false
    }


    fun sort() {
        Arrays.sort(data, 0, count)
    }
}
