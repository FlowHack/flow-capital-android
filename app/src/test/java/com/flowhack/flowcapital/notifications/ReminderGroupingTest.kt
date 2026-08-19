package com.flowhack.flowcapital.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты группировки умных напоминаний БП/СБП по времени последнего нажатия.
 * Проверяют:
 * - Пустой список -> пустой результат
 * - Одиночное событие -> один кластер
 * - События в пределах ±1 минуты объединяются в один кластер
 * - События с разницей более минуты разделяются на разные кластеры
 * - Каскадная кластеризация (цепочка событий в пределах минуты)
 */
class ReminderGroupingTest {

    private val minute = 60_000L

    @Test
    fun withPressTimes_empty_returnsEmpty() {
        assertEquals("Пустой список -> пусто", emptyList<List<Long>>(),
            ReminderGrouping.withPressTimes(emptyList()))
    }

    @Test
    fun withPressTimes_single_returnsOneCluster() {
        val result = ReminderGrouping.withPressTimes(listOf(1_000L))
        assertEquals("Одно событие -> один кластер", 1, result.size)
        assertEquals(listOf(1_000L), result.first())
    }

    @Test
    fun withPressTimes_withinMinute_mergesIntoOneCluster() {
        val t1 = 1_000_000L
        val t2 = t1 + 30_000L // +30 сек
        val result = ReminderGrouping.withPressTimes(listOf(t1, t2))
        assertEquals("События в пределах минуты объединяются", 1, result.size)
        assertEquals(listOf(t1, t2), result.first())
    }

    @Test
    fun withPressTimes_exactlyMinute_mergesIntoOneCluster() {
        val t1 = 1_000_000L
        val t2 = t1 + minute // ровно +60 сек
        val result = ReminderGrouping.withPressTimes(listOf(t1, t2))
        assertEquals("Разница ровно в минуту — один кластер", 1, result.size)
    }

    @Test
    fun withPressTimes_moreThanMinute_splitsIntoClusters() {
        val t1 = 1_000_000L
        val t2 = t1 + 3 * minute // +3 минуты
        val result = ReminderGrouping.withPressTimes(listOf(t1, t2))
        assertEquals("Разница более минуты -> два кластера", 2, result.size)
    }

    @Test
    fun withPressTimes_cascade_mergesChain() {
        // t1 -> t2 (+30с) -> t3 (+30с): вся цепочка в пределах минуты между соседями
        val t1 = 1_000_000L
        val t2 = t1 + 30_000L
        val t3 = t2 + 30_000L
        val result = ReminderGrouping.withPressTimes(listOf(t1, t2, t3))
        assertEquals("Каскадная цепочка объединяется в один кластер", 1, result.size)
        assertEquals(listOf(t1, t2, t3), result.first())
    }

    @Test
    fun withPressTimes_unsortedInput_sortsBeforeClustering() {
        val t1 = 1_000_000L
        val t2 = t1 + 20_000L
        val result = ReminderGrouping.withPressTimes(listOf(t2, t1))
        assertEquals("Несортированный вход -> один кластер", 1, result.size)
        assertEquals("Внутри кластера времена отсортированы", listOf(t1, t2), result.first())
    }
}