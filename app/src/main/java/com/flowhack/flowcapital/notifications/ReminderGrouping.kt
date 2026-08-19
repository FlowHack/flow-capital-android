package com.flowhack.flowcapital.notifications

/**
 * Чистые функции группировки умных напоминаний БП/СБП.
 *
 * Пользователь может нажать кнопку по нескольким потокам практически
 * одновременно. Чтобы не спамить отдельными уведомлениями «Вчера в это время…»,
 * события, совершённые в пределах ±1 минуты, объединяются в одно напоминание.
 */
object ReminderGrouping {

    /** Окно кластеризации: ±1 минута (60 000 мс). */
    const val CLUSTER_WINDOW_MILLIS = 60_000L

    /**
     * Группирует времена последних нажатий в кластеры «одного времени».
     *
     * Алгоритм: времена сортируются по возрастанию; очередное событие попадает
     * в текущий кластер, если отстоит от предыдущего события не более чем на
     * [CLUSTER_WINDOW_MILLIS] (±1 минута), иначе начинается новый кластер.
     *
     * @param times Времена нажатий (Unix timestamp, мс)
     * @return Список кластеров (внутри каждого времена отсортированы по возрастанию)
     */
    fun withPressTimes(times: List<Long>): List<List<Long>> {
        if (times.isEmpty()) return emptyList()
        val sorted = times.sorted()
        val result = mutableListOf<MutableList<Long>>()
        var current = mutableListOf(sorted.first())
        var lastTime = sorted.first()
        for (time in sorted.drop(1)) {
            if (time - lastTime <= CLUSTER_WINDOW_MILLIS) {
                current.add(time)
            } else {
                result.add(current)
                current = mutableListOf(time)
            }
            lastTime = time
        }
        result.add(current)
        return result
    }
}