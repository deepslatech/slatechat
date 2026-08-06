package ch.deepslate.slatechat.core.api

import org.bukkit.permissions.Permissible
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePermissible(
    private val granted: Set<String> = emptySet(),
    private val op: Boolean = false
) : Permissible {
    override fun isPermissionSet(name: String) = name in granted
    override fun isPermissionSet(perm: Permission) = isPermissionSet(perm.name)
    override fun hasPermission(name: String) = name in granted
    override fun hasPermission(perm: Permission) = hasPermission(perm.name)
    override fun isOp() = op
    override fun setOp(value: Boolean) {}
    override fun addAttachment(plugin: Plugin, name: String, value: Boolean): PermissionAttachment =
        throw UnsupportedOperationException("not needed for these tests")
    override fun addAttachment(plugin: Plugin): PermissionAttachment =
        throw UnsupportedOperationException("not needed for these tests")
    override fun addAttachment(plugin: Plugin, name: String, value: Boolean, ticks: Int): PermissionAttachment =
        throw UnsupportedOperationException("not needed for these tests")
    override fun addAttachment(plugin: Plugin, ticks: Int): PermissionAttachment =
        throw UnsupportedOperationException("not needed for these tests")
    override fun removeAttachment(attachment: PermissionAttachment) {}
    override fun recalculatePermissions() {}
    override fun getEffectivePermissions(): MutableSet<PermissionAttachmentInfo> = mutableSetOf()
}

private class FakeChannel(
    override val restrict: Boolean,
    override val permission: String?
) : Channel {
    override val name = "test"
    override val command = "test"
    override val format = "<message>"
    override val broadcast = false
    override val radius = 0
    override val actionbar = false
    override fun load() {}
}

class ChannelTest {

    @Test
    fun `unrestricted channel never blocks anyone`() {
        val channel = FakeChannel(restrict = false, permission = "some.node")
        assertFalse(channel.blocksSender(FakePermissible()))
    }

    @Test
    fun `restricted channel blocks senders without the node, lets senders with it through`() {
        val channel = FakeChannel(restrict = true, permission = "chat.vip")
        assertTrue(channel.blocksSender(FakePermissible()))
        assertFalse(channel.blocksSender(FakePermissible(granted = setOf("chat.vip"))))
    }

    @Test
    fun `restricted channel with no permission configured blocks everyone except OP`() {
        // Regression: this used to block OP too, unlike every other permission check in the
        // plugin (undeclared nodes fall back to OP-only by default).
        val channel = FakeChannel(restrict = true, permission = null)
        assertTrue(channel.blocksSender(FakePermissible()))
        assertFalse(channel.blocksSender(FakePermissible(op = true)))
    }
}
