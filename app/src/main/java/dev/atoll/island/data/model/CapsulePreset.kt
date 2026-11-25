package dev.atoll.island.data.model

// misure dell'isola su iOS, dalla più stretta alla più larga
enum class CapsulePreset(
    val id: String,
    val displayName: String,
    val shortName: String,
    // larghezza schermo in pt, in verticale
    val screenWidthPt: Float,
    // diagonale in pollici, dalle schede tecniche Apple
    val diagonalInches: Float,
    val widthPt: Float,
    val heightPt: Float,
    val physicalWidthMm: Float,
    val screenWidthRatio: Float,
    val isCustom: Boolean = false
) {
    IPHONE_18_PRO_MAX(
        id = "iphone_18_pro_max",
        displayName = "iPhone 18 Pro Max",
        shortName = "18 Pro Max",
        screenWidthPt = 440f,
        diagonalInches = 6.9f,
        widthPt = 94.67f,
        heightPt = 36.67f,
        physicalWidthMm = 15.68f,
        screenWidthRatio = 0.2152f
    ),
    IPHONE_18_PRO(
        id = "iphone_18_pro",
        displayName = "iPhone 18 Pro",
        shortName = "18 Pro",
        screenWidthPt = 402f,
        diagonalInches = 6.3f,
        widthPt = 94.67f,
        heightPt = 36.67f,
        physicalWidthMm = 15.68f,
        screenWidthRatio = 0.2355f
    ),
    IPHONE_16_17_PRO_MAX(
        id = "iphone_16_17_pro_max",
        displayName = "iPhone 16 / 17 Pro Max",
        shortName = "16 / 17 Pro Max",
        screenWidthPt = 440f,
        diagonalInches = 6.9f,
        widthPt = 125.33f,
        heightPt = 36.67f,
        physicalWidthMm = 20.76f,
        screenWidthRatio = 0.2848f
    ),
    IPHONE_14_16_PLUS_MAX(
        id = "iphone_14_16_plus_max",
        displayName = "iPhone 14 Pro Max / 15 Plus / 15 Pro Max / 16 Plus",
        shortName = "15 / 16 Plus",
        screenWidthPt = 430f,
        diagonalInches = 6.7f,
        widthPt = 125.33f,
        heightPt = 36.67f,
        physicalWidthMm = 20.76f,
        screenWidthRatio = 0.2915f
    ),
    IPHONE_AIR(
        id = "iphone_air",
        displayName = "iPhone Air",
        shortName = "iPhone Air",
        screenWidthPt = 420f,
        diagonalInches = 6.5f,
        widthPt = 125.33f,
        heightPt = 36.67f,
        physicalWidthMm = 20.76f,
        screenWidthRatio = 0.2984f
    ),
    IPHONE_16_PRO_17(
        id = "iphone_16_pro_17",
        displayName = "iPhone 16 Pro / 17 / 17 Pro",
        shortName = "16 Pro / 17",
        screenWidthPt = 402f,
        diagonalInches = 6.3f,
        widthPt = 125.33f,
        heightPt = 36.67f,
        physicalWidthMm = 20.76f,
        screenWidthRatio = 0.3118f
    ),
    IPHONE_14_PRO_16(
        id = "iphone_14_pro_16",
        displayName = "iPhone 14 Pro / 15 / 15 Pro / 16",
        shortName = "14 Pro / 15 / 16",
        screenWidthPt = 393f,
        diagonalInches = 6.1f,
        widthPt = 125.00f,
        heightPt = 36.67f,
        physicalWidthMm = 20.71f,
        screenWidthRatio = 0.3181f
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        shortName = "Custom",
        screenWidthPt = 0f,
        diagonalInches = 0f,
        widthPt = 125.33f,
        heightPt = 36.67f,
        physicalWidthMm = 20.76f,
        screenWidthRatio = 0.3118f,
        isCustom = true
    );

    companion object {
        val devicePresets: List<CapsulePreset>
            get() = entries.filter { !it.isCustom }

        fun fromId(id: String?): CapsulePreset {
            if (id.isNullOrBlank()) return IPHONE_16_PRO_17
            entries.firstOrNull { it.id == id || it.name == id }?.let { return it }
            return when (id) {
                "iphone_16_pro", "IPHONE_16_PRO" -> IPHONE_16_PRO_17
                "iphone_14_pro_max", "IPHONE_14_PRO_MAX" -> IPHONE_14_16_PLUS_MAX
                "max_stretch", "MAX_STRETCH" -> IPHONE_14_PRO_16
                else -> IPHONE_16_PRO_17
            }
        }
    }
}

