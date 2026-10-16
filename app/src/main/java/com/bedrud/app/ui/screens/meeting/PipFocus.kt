package com.bedrud.app.ui.screens.meeting

/**
 * Who the picture-in-picture window shows, in order: the stream being watched; whoever among the
 * others is speaking loudest; the last one shown, so a pause between sentences does not send the
 * window back to whoever joined first; the first other participant; and you only when nobody else
 * is in the call. Your own voice never takes the window over, since it shows the call, not you.
 */
internal fun pipFocusIdentity(
    watchedStreamIdentity: String?,
    speakingLevels: Map<String, Float>,
    previousFocus: String?,
    remoteIdentities: List<String>,
    localIdentity: String?,
): String? {
    if (watchedStreamIdentity != null) return watchedStreamIdentity
    val loudestOther = remoteIdentities
        .filter { identity -> (speakingLevels[identity] ?: 0f) > 0f }
        .maxByOrNull { identity -> speakingLevels.getValue(identity) }
    return loudestOther
        ?: previousFocus?.takeIf { it in remoteIdentities }
        ?: remoteIdentities.firstOrNull()
        ?: localIdentity
}
