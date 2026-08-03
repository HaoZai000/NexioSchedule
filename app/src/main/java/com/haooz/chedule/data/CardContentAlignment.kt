package com.haooz.chedule.data

enum class CardContentAlignment(val label: String) {
    TOP_START("顶部居左"),
    TOP_CENTER("顶部居中"),
    CENTER_START("中间居左"),
    CENTER_CENTER("中间居中");

    companion object {
        fun fromOrdinal(ordinal: Int): CardContentAlignment {
            return entries.getOrElse(ordinal) { CENTER_CENTER }
        }
    }
}
