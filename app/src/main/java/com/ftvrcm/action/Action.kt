package com.ftvrcm.action

interface Action {
    val id: String
    val description: String

    fun execute()
}
