package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.audio.AudioService;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Hands a player a link to listen to a computer.
 *
 * <p>Minecraft has no way to carry guest audio -- its sound packet takes a name and a pitch, never
 * samples -- so the audio leaves through a browser instead. This is entirely opt-in: nothing is
 * streamed, and the guest's sound card stays idle, until somebody actually opens their link.
 */
public class Audio extends ComputerSubCommand {

    @Override
    public String getName() {
        return "audio";
    }

    @Override
    public String getDescription() {
        return "Gives you a link to hear the computer you are looking at.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers audio [id]";
    }

    @Override
    public void perform(Player player, String[] args) {
        VMComputers plugin = VMComputers.getPlugin();
        AudioService audio = plugin.getAudioService();
        if (audio == null || !audio.isRunning()) {
            player.sendMessage(ChatColor.RED + "The audio server is not running. Check the console "
                    + "and audio.enabled / audio.port in config.yml.");
            return;
        }

        Integer computerId = resolveComputer(player, args);
        if (computerId == null) {
            player.sendMessage(ChatColor.RED + "Look at a running computer, or name one: "
                    + getSyntax());
            return;
        }

        VirtualMachine machine = ComputerFunctions.get(computerId.intValue());
        if (machine == null || !machine.isRunning()) {
            player.sendMessage(ChatColor.RED + "Computer #" + computerId + " is not running.");
            return;
        }

        String link = audio.linkFor(computerId.intValue(),
                audio.issueToken(player.getUniqueId()));

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Audio for computer #" + computerId);

        TextComponent message = new TextComponent("  " + link);
        message.setColor(net.md_5.bungee.api.ChatColor.WHITE);
        message.setUnderlined(true);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Opens in your browser. Press Listen once it loads.")));
        player.spigot().sendMessage(message);

        player.sendMessage(ChatColor.GRAY + "  Only you can use this link, and it stops working "
                + "when you leave the server.");
        player.sendMessage("");
    }

    /** The named computer, or whichever one the player is looking at. */
    private Integer resolveComputer(Player player, String[] args) {
        if (args.length > 1) {
            try {
                return Integer.valueOf(Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return VMComputers.getPlugin().getPointerListener().targetComputerId(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
