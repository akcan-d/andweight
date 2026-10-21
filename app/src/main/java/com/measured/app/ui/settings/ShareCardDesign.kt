package com.measured.app.ui.settings

/** [CLASSIC] is free for everyone; the rest are a supporter perk. The picker itself is always
 * shown so the other designs stay discoverable - a locked one shows as locked rather than being
 * left out of the picker entirely. */
enum class ShareCardDesign(val label: String, val isSupporterOnly: Boolean) {
    CLASSIC("Classic", isSupporterOnly = false),
    MINIMAL("Minimal", isSupporterOnly = true),
    STATS("Two stats", isSupporterOnly = true),
}
