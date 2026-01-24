package me.luucx7.simplexchat.core.api

interface Local : Channel {
    var enabled: Boolean
    fun isEnabled(): Boolean
}
