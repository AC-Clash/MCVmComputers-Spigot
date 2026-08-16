package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.display.MonitorScreen;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Toggles drawing the pointer onto the screen, for testing.
 *
 * <p>The pointer is normally invisible, and that is the intended state: drawing it dirties map
 * panels on every head movement, and the arrow reaches the player a map packet behind their own
 * crosshair, so it visibly trails what it is following. Minecraft's crosshair is already exactly
 * where the pointer is.
 *
 * <p>But "invisible and working" and "invisible and broken" look identical, and a guest that draws
 * its own cursor on a hardware plane sends nothing over VNC to check against. So this exists: turn
 * it on, watch the arrow, confirm the plugin is aiming where you think it is, turn it off again.
 * Global rather than per player, because the arrow is painted into a framebuffer that everyone
 * looking at that screen shares.
 */
public class Debug extends ComputerSubCommand {

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public String getDescription() {
        return "Toggles drawing the mouse pointer onto screens, for testing.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers debug";
    }

    @Override
    public String getPermission() {
        return com.acclash.vmcomputers.utils.Permissions.ADMIN;
    }

    @Override
    public void perform(Player player, String[] args) {
        VMComputers plugin = VMComputers.getPlugin();
        boolean enabled = !plugin.isPointerDebug();
        plugin.setPointerDebug(enabled);

        if (enabled) {
            player.sendMessage(ChatColor.GREEN + "Pointer debug " + ChatColor.WHITE + "on"
                    + ChatColor.GREEN + ". An arrow will follow your crosshair on any screen you"
                    + " look at.");
            player.sendMessage(ChatColor.GRAY + "It will lag behind your crosshair -- that is the"
                    + " map protocol, not the aim. The crosshair is the true position.");
        } else {
            // Take the arrows off the screens as well as stopping new ones, or the last one drawn
            // stays burned into the framebuffer until the guest happens to repaint that area.
            for (MonitorScreen screen : plugin.screens()) {
                screen.hideCursor();
            }
            player.sendMessage(ChatColor.YELLOW + "Pointer debug " + ChatColor.WHITE + "off"
                    + ChatColor.YELLOW + ".");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
