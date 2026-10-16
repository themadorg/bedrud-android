package com.bedrud.app.core.pip

/** A window shape, as a width over a height. */
data class PipAspect(val width: Int, val height: Int) {
    val ratio: Float get() = width.toFloat() / height

    companion object {
        /** For an avatar and a name chip, which need no width to spare. */
        val Square = PipAspect(1, 1)

        /**
         * The narrowest and widest windows Android allows by default, 1:2.39 and 2.39:1. It throws
         * for a window asked outside them, so a video beyond either is shown within it.
         */
        val Narrowest = PipAspect(100, 239)
        val Widest = PipAspect(239, 100)
    }
}

/** A rectangle on screen, in pixels. */
data class PipBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The window the call would shrink to: its [aspect], and the part of the screen it grows from,
 * when the tile it will show is on screen to grow from.
 */
data class PipWindow(val aspect: PipAspect, val sourceRect: PipBounds?)

/**
 * The shape of the picture-in-picture window: the video it shows, portrait or landscape, kept
 * within what Android allows; square when it shows no video, or one that has not reported its size.
 */
internal fun pipAspect(videoWidth: Int?, videoHeight: Int?): PipAspect {
    if (videoWidth == null || videoHeight == null || videoWidth <= 0 || videoHeight <= 0) {
        return PipAspect.Square
    }
    val ratio = videoWidth.toFloat() / videoHeight
    return when {
        ratio > PipAspect.Widest.ratio -> PipAspect.Widest
        ratio < PipAspect.Narrowest.ratio -> PipAspect.Narrowest
        else -> PipAspect(videoWidth, videoHeight)
    }
}

/**
 * The part of [bounds] the window grows from: the largest rectangle of the window's own shape that
 * fits, centred. Android draws the app's icon over the window while it opens unless it is told which
 * part of the screen becomes the window, in that shape.
 */
internal fun pipSourceRect(bounds: PipBounds, aspect: PipAspect): PipBounds {
    val width = bounds.right - bounds.left
    val height = bounds.bottom - bounds.top
    val fitsByWidth = width.toLong() * aspect.height <= height.toLong() * aspect.width
    val fittedWidth = if (fitsByWidth) width else (height.toLong() * aspect.width / aspect.height).toInt()
    val fittedHeight = if (fitsByWidth) (width.toLong() * aspect.height / aspect.width).toInt() else height
    val left = bounds.left + (width - fittedWidth) / 2
    val top = bounds.top + (height - fittedHeight) / 2
    return PipBounds(left, top, left + fittedWidth, top + fittedHeight)
}
