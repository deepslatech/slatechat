package ch.deepslate.slatechat.core.format

import org.bukkit.permissions.Permissible
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Minimal Permissible test double -- only hasPermission(String) is backed by real data (a
 * Set of granted node names). Everything else the interface requires is either unused by
 * MineDownConverter or stubbed to a value that would make a misuse obvious.
 */
private class FakePermissible(private val granted: Set<String> = emptySet()) : Permissible {
    override fun isPermissionSet(name: String) = name in granted
    override fun isPermissionSet(perm: Permission) = isPermissionSet(perm.name)
    override fun hasPermission(name: String) = name in granted
    override fun hasPermission(perm: Permission) = hasPermission(perm.name)
    override fun isOp() = false
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

class MineDownConverterTest {

    private val colored = FakePermissible(setOf("chat.colored"))
    private val hoverBypass = FakePermissible(setOf("chat.hover.bypass"))
    private val noPerms = FakePermissible()

    @Test
    fun `solid color applies with chat colored permission`() {
        assertEquals(
            "<red>Text<reset>",
            MineDownConverter.convert("[Text](red)", colored)
        )
    }

    @Test
    fun `solid color is skipped without chat colored permission`() {
        assertEquals("Text", MineDownConverter.convert("[Text](red)", noPerms))
    }

    @Test
    fun `solid color is skipped with a null sender`() {
        assertEquals("Text", MineDownConverter.convert("[Text](red)", null))
    }

    @Test
    fun `underlined formatting works (regression -- used to be the invalid tag name 'underline')`() {
        assertEquals("<underlined>Text</underlined>", MineDownConverter.convert("[Text](underlined)", noPerms))
    }

    @Test
    fun `bang-prefixed decoration inverts it, using MiniMessage's own bang-closing syntax`() {
        assertEquals("<!bold>Text</!bold>", MineDownConverter.convert("[Text](!bold)", noPerms))
    }

    @Test
    fun `gradient shorthand applies with permission and is gated without it`() {
        assertEquals(
            "<gradient:#FF0000:#0000FF>Text</gradient>",
            MineDownConverter.convert("[Text](#FF0000-#0000FF)", colored)
        )
        assertEquals("Text", MineDownConverter.convert("[Text](#FF0000-#0000FF)", noPerms))
    }

    @Test
    fun `bold formatting applies regardless of permission`() {
        assertEquals("<bold>Text</bold>", MineDownConverter.convert("[Text](bold)", noPerms))
    }

    @Test
    fun `command link gets automatic hover showing the command`() {
        assertEquals(
            "<hover:show_text:'/kill'><click:run_command:/kill>Click here</click></hover>",
            MineDownConverter.convert("[Click here](/kill)", noPerms)
        )
    }

    @Test
    fun `custom hover text is ignored without chat hover bypass, falls back to automatic`() {
        assertEquals(
            "<hover:show_text:'/kill'><click:run_command:/kill>Click here</click></hover>",
            MineDownConverter.convert("[Click here](/kill Custom hover)", noPerms)
        )
    }

    @Test
    fun `custom hover text is used with chat hover bypass`() {
        assertEquals(
            "<hover:show_text:'Custom hover'><click:run_command:/kill>Click here</click></hover>",
            MineDownConverter.convert("[Click here](/kill Custom hover)", hoverBypass)
        )
    }

    @Test
    fun `hover-only text with no link needs no permission`() {
        assertEquals(
            "<hover:show_text:'Hover text'>Info</hover>",
            MineDownConverter.convert("[Info](Hover text)", noPerms)
        )
    }

    @Test
    fun `escaped syntax is passed through literally, not converted`() {
        assertEquals("[Click here](/kill)", MineDownConverter.convert("\\[Click here](/kill)", colored))
    }
}
