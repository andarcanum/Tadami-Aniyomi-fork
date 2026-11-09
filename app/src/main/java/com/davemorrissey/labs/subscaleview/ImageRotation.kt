package com.davemorrissey.labs.subscaleview

enum class ImageRotation(val rotation: Int) {
    ROTATION_0(0),
    ROTATION_90(90),
    ROTATION_180(180),
    ROTATION_270(270),
    ;

    fun rotateBy90Degrees(): ImageRotation = when (this) {
        ROTATION_0 -> ROTATION_90
        ROTATION_90 -> ROTATION_180
        ROTATION_180 -> ROTATION_270
        ROTATION_270 -> ROTATION_0
    }
}
