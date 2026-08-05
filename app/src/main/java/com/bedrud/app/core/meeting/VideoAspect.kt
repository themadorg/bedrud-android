package com.bedrud.app.core.meeting

/**
 * The 16:9 video aspect ratio shared by the Picture-in-Picture window and the in-meeting
 * screen-share/video panes, so the PiP ratio and the on-screen panes never drift apart.
 */
object VideoAspect {
    const val WIDTH = 16
    const val HEIGHT = 9

    /** [WIDTH]:[HEIGHT] as a float, for Compose `Modifier.aspectRatio`. */
    val RATIO: Float = WIDTH.toFloat() / HEIGHT
}
