package ch.deepslate.slatechat.core.api

interface Local : Channel {
    var enabled: Boolean
    fun isEnabled(): Boolean
}
