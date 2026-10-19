package com.andweight.app.domain

/** The free default, plus two supporter-only alternates - drives both the in-app color palette
 * and (where the platform allows it) the launcher icon color, so switching one switches both. */
enum class AccentTheme(val label: String, val isSupporterOnly: Boolean) {
    SAGE("Sage", isSupporterOnly = false),
    BERRY("Berry", isSupporterOnly = true),
    OCEAN("Ocean", isSupporterOnly = true),
}
