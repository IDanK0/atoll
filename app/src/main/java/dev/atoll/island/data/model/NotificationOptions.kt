package dev.atoll.island.data.model

enum class NotificationChannelMode(
    val channelId: String
) {
    LOW("atoll_live_low"),
    DEFAULT("atoll_live_default");

    companion object {
        fun fromName(name: String?): NotificationChannelMode =
            entries.firstOrNull { it.name == name || it.channelId == name } ?: LOW
    }
}

enum class NotificationTemplateStyle {
    STANDARD,
    PROGRESS_STYLE;

    companion object {
        fun fromName(name: String?): NotificationTemplateStyle =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class SmallIconMode {
    TRANSPARENT,
    DOT,
    CAPSULE;

    companion object {
        fun fromName(name: String?): SmallIconMode =
            entries.firstOrNull { it.name == name } ?: TRANSPARENT
    }
}

enum class TitleMode {
    BRAILLE_BLANK,
    SUBTLE_TEXT;

    companion object {
        fun fromName(name: String?): TitleMode =
            entries.firstOrNull { it.name == name } ?: BRAILLE_BLANK
    }
}
