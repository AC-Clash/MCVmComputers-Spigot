package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.parts.EChair;
import com.acclash.vmcomputers.rfb.RfbClient;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads and rebinds what the chair's movement keys do in the guest.
 *
 * <p>The chair sends six inputs, and which keys they arrive as is the player's choice. Two
 * profiles rather than one, because a game and a menu want different keys and a player switches
 * between them constantly -- swapping hands in the chair flips between them without opening chat.
 */
public class Keys extends ComputerSubCommand {

    @Override
    public String getName() {
        return "keys";
    }

    @Override
    public String getDescription() {
        return "Shows or changes what the chair's keys send";
    }

    @Override
    public String getSyntax() {
        return "/vmcomputers keys [game|menu|bind <input> <key>|reset]";
    }

    @Override
    public void perform(Player player, String[] args) {
        EChair.KeyMode mode = EChair.modeOf(player);

        if (args.length < 2) {
            show(player, mode);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("game".equals(action) || "menu".equals(action)) {
            EChair.KeyMode chosen = "game".equals(action)
                    ? EChair.KeyMode.GAME : EChair.KeyMode.MENU;
            EChair.setMode(player, chosen);
            player.sendMessage(ChatColor.AQUA + "Chair profile: " + ChatColor.WHITE + chosen.name()
                    + ChatColor.GRAY + " (" + chosen.purpose() + ")");
            show(player, chosen);
            return;
        }

        if ("reset".equals(action)) {
            EChair.resetBindings(player, mode);
            player.sendMessage(ChatColor.YELLOW + "The " + mode.name()
                    + " profile is back to its defaults.");
            show(player, mode);
            return;
        }

        if ("bind".equals(action)) {
            bind(player, mode, args);
            return;
        }

        player.sendMessage(ChatColor.RED + "Usage: " + getSyntax());
    }

    private void bind(Player player, EChair.KeyMode mode, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED
                    + "Usage: /vmcomputers keys bind <forward|left|back|right|jump|sprint> <key>");
            return;
        }

        EChair.Key input = EChair.keyByName(args[2]);
        if (input == null) {
            player.sendMessage(ChatColor.RED + "'" + args[2] + "' is not one of the chair's keys.");
            player.sendMessage(ChatColor.GRAY + "Those are: " + inputNames());
            return;
        }

        Integer keysym = RfbClient.Keysym.byName(args[3]);
        if (keysym == null) {
            player.sendMessage(ChatColor.RED + "'" + args[3] + "' is not a key I can send.");
            player.sendMessage(ChatColor.GRAY
                    + "Try a single character, or one of: " + String.join(", ", RfbClient.Keysym.NAMES));
            return;
        }

        EChair.bind(player, mode, input, keysym.intValue());
        player.sendMessage(ChatColor.GREEN + input.name().toLowerCase(Locale.ROOT) + " now sends "
                + ChatColor.WHITE + RfbClient.Keysym.nameOf(keysym.intValue())
                + ChatColor.GRAY + " in the " + mode.name() + " profile.");
    }

    private void show(Player player, EChair.KeyMode mode) {
        player.sendMessage(ChatColor.AQUA + "Chair keys " + ChatColor.GRAY + "(profile: "
                + ChatColor.WHITE + mode.name() + ChatColor.GRAY + ", " + mode.purpose() + ")"
                + (EChair.isCustomised(player, mode) ? ChatColor.DARK_GRAY + " [edited]" : ""));

        int[] bound = EChair.bindingsOf(player, mode);
        for (EChair.Key key : EChair.KEYS) {
            player.sendMessage(ChatColor.GRAY + "  " + pad(key.name().toLowerCase(Locale.ROOT))
                    + ChatColor.WHITE + RfbClient.Keysym.nameOf(bound[key.ordinal()]));
        }
        player.sendMessage(ChatColor.DARK_GRAY
                + "  Swap hands while seated to switch profile.");
    }

    private static String pad(String name) {
        StringBuilder padded = new StringBuilder(name);
        while (padded.length() < 9) {
            padded.append(' ');
        }
        return padded.toString();
    }

    private static String inputNames() {
        List<String> names = new ArrayList<String>();
        for (EChair.Key key : EChair.KEYS) {
            names.add(key.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("game", "menu", "bind", "reset");
        }
        if (args.length == 3 && "bind".equalsIgnoreCase(args[1])) {
            List<String> names = new ArrayList<String>();
            for (EChair.Key key : EChair.KEYS) {
                names.add(key.name().toLowerCase(Locale.ROOT));
            }
            return names;
        }
        if (args.length == 4 && "bind".equalsIgnoreCase(args[1])) {
            return Arrays.asList(RfbClient.Keysym.NAMES);
        }
        return Arrays.asList();
    }
}
