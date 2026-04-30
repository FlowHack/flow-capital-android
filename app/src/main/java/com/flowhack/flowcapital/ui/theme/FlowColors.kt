package com.flowhack.flowcapital.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Объект с константами цветов для потоков.
 * Каждый поток имеет свой цвет для визуального различия.
 */
object FlowColors {
    /** Цвет Потока Новичка (синий) */
    val PN_COLOR = Color(0xFF2196F3)

    /** Цвет Быстрого Потока (оранжевый) */
    val BP_COLOR = Color(0xFFFF9800)

    /** Цвет Премиум Стартового Потока (красный) */
    val PSP_COLOR = Color(0xFFF44336)

    /** Цвет Растущего Потока (зелёный) */
    val RP_COLOR = Color(0xFF4CAF50)

    /** Цвет Накапливающего Потока (фиолетовый) */
    val NP_COLOR = Color(0xFF9C27B0)

    /**
     * Получить цвет потока по индексу вкладки.
     *
     * @param index Индекс вкладки (0-4)
     * @return Цвет потока
     */
    fun getColorForIndex(index: Int): Color {
        return when (index) {
            0 -> PN_COLOR
            1 -> BP_COLOR
            2 -> PSP_COLOR
            3 -> RP_COLOR
            4 -> NP_COLOR
            else -> RP_COLOR
        }
    }
}
