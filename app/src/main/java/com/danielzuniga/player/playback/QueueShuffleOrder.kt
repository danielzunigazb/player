package com.danielzuniga.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * The play order while shuffling. Media3's default scatters every added item at random, which
 * broke two things: "play next" and "add to queue" landed anywhere in the shuffled order, and a
 * shuffled queue started from a random point in it, so everything before that point never played.
 *
 * Here a new queue is shuffled with [startingWith] the song that's playing, items inserted into a
 * queue go right after the item before them in the list (after the current song for "play next")
 * and items appended to the list go last.
 */
@UnstableApi
class QueueShuffleOrder private constructor(
    private val order: IntArray,
    private val random: Random,
    /**
     * Shuffled for a whole new list rather than kept from the previous one; the service then
     * reshuffles it from the song that plays. Removing or inserting items keeps the order.
     */
    val fresh: Boolean = false,
) : ShuffleOrder {

    /** Where each list index sits in the play order. */
    private val position = IntArray(order.size).also { pos -> order.forEachIndexed { i, index -> pos[index] = i } }

    constructor(length: Int, random: Random = Random.Default) : this(shuffled(length, random), random, fresh = true)

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int =
        position[index].let { if (it + 1 < order.size) order[it + 1] else C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int =
        position[index].let { if (it > 0) order[it - 1] else C.INDEX_UNSET }

    override fun getLastIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.last()

    override fun getFirstIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (order.isEmpty()) return QueueShuffleOrder(insertionCount, random)
        val inserted = IntArray(insertionCount) { insertionIndex + it }
        val at = when (insertionIndex) {
            order.size -> order.size
            0 -> 0
            else -> position[insertionIndex - 1] + 1
        }
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }
        return QueueShuffleOrder((shifted.take(at) + inserted.toList() + shifted.drop(at)).toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        val kept = order.filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removed else it }
        return QueueShuffleOrder(kept.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder(IntArray(0), random)

    /** Play order as list indices, first to last. */
    internal fun toList(): List<Int> = order.toList()

    companion object {
        /** A new shuffle of [length] items that plays [first] before all the others. */
        fun startingWith(first: Int, length: Int, random: Random = Random.Default): QueueShuffleOrder {
            val rest = (0 until length).filter { it != first }.shuffled(random)
            val order = if (first in 0 until length) listOf(first) + rest else rest
            return QueueShuffleOrder(order.toIntArray(), random)
        }

        private fun shuffled(length: Int, random: Random) = (0 until length).shuffled(random).toIntArray()
    }
}
