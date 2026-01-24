package me.luucx7.simplexchat.core.api

interface Channel {

    val name: String
    val command: String
    val format: String
    val broadcast: Boolean
    val restrict: Boolean
    val permission: String?
    val radius: Int
    val actionbar: Boolean

    fun load()
}
