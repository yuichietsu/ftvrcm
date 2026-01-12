package com.ftvrcm.domain

enum class OperationMode {
    NORMAL,
    MOUSE;

    fun toggle(): OperationMode = when (this) {
        NORMAL -> MOUSE
        MOUSE -> NORMAL
    }
}
