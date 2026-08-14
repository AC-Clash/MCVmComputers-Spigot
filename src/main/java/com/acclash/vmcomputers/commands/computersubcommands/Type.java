package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.rfb.RfbClient;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Sends keystrokes to whichever computer the player is looking at.
 *
 * <p>Vanilla clients transmit no keyboard input, so text has to arrive some other way. A
 * host-rendered on-screen keyboard driven by the look pointer is the eventual answer, but this
 * covers the same ground through chat and is far quicker to type with -- entering a hostname or a
 * password by clicking letters would be miserable.
 *
 * <p>Special keys are named rather than typed: {@code /vmcomputers type @RETURN}. The prefix keeps
 * them distinguishable from literal text without needing a second command.
 */
public class Type extends ComputerSubCommand {

    /** Named keys, addressed as {@code @NAME}. */
    private static final String[] SPECIAL_NAMES = {
            "RETURN", "ENTER", "TAB", "ESC", "ESCAPE", "BACKSPACE", "DELETE", "SPACE",
            "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGEUP", "PAGEDOWN",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    };

    @Override
    public String getName() {
        return "type";
    }

    @Override
    public String getDescription() {
        return "Types text into the computer you are looking at.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers type <text>   or   /vmcomputers type @RETURN";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(getSyntax());
            player.sendMessage(ChatColor.GRAY + "Special keys: @RETURN @TAB @ESC @BACKSPACE "
                    + "@UP @DOWN @LEFT @RIGHT @F1..@F12");
            return;
        }

        Integer computerId = VMComputers.getPlugin().getPointerListener()
                .targetComputerId(player);
        if (computerId == null) {
            player.sendMessage(ChatColor.YELLOW + "Look at a running computer's screen first.");
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(computerId.intValue());
        if (machine == null || !machine.isRunning()) {
            player.sendMessage(ChatColor.YELLOW + "That computer is not running.");
            return;
        }

        // Chat collapses runs of spaces into separate arguments, so rejoin with single spaces.
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[i]);
        }

        int sent = send(machine, text.toString(), player);
        if (sent > 0) {
            player.sendMessage(ChatColor.GRAY + "Sent " + sent + " keystroke(s) to computer #"
                    + computerId + ".");
        }
    }

    private int send(VirtualMachine machine, String text, Player player) {
        int sent = 0;
        String[] tokens = text.split(" ");
        for (int t = 0; t < tokens.length; t++) {
            // split() drops the separators, so put a space back between tokens -- but only
            // between them, never after the last one.
            if (t > 0) {
                tap(machine, ' ');
                sent++;
            }

            String token = tokens[t];
            if (token.startsWith("@") && token.length() > 1) {
                Integer keysym = specialKeysym(token.substring(1));
                if (keysym == null) {
                    player.sendMessage(ChatColor.RED + "Unknown key '" + token + "'.");
                    continue;
                }
                tap(machine, keysym.intValue());
                sent++;
                continue;
            }

            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                try {
                    tap(machine, RfbClient.Keysym.ofChar(c));
                    sent++;
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Cannot type '" + c + "'.");
                }
            }
        }
        return sent;
    }

    private static void tap(VirtualMachine machine, int keysym) {
        machine.sendKey(keysym, true);
        machine.sendKey(keysym, false);
    }

    private static Integer specialKeysym(String rawName) {
        String name = rawName.toUpperCase(Locale.ROOT);
        if (name.matches("F([1-9]|1[0-2])")) {
            return Integer.valueOf(RfbClient.Keysym.f(Integer.parseInt(name.substring(1))));
        }
        switch (name) {
            case "RETURN":
            case "ENTER":
                return Integer.valueOf(RfbClient.Keysym.RETURN);
            case "TAB":
                return Integer.valueOf(RfbClient.Keysym.TAB);
            case "ESC":
            case "ESCAPE":
                return Integer.valueOf(RfbClient.Keysym.ESCAPE);
            case "BACKSPACE":
                return Integer.valueOf(RfbClient.Keysym.BACKSPACE);
            case "DELETE":
                return Integer.valueOf(RfbClient.Keysym.DELETE);
            case "SPACE":
                return Integer.valueOf(' ');
            case "UP":
                return Integer.valueOf(RfbClient.Keysym.UP);
            case "DOWN":
                return Integer.valueOf(RfbClient.Keysym.DOWN);
            case "LEFT":
                return Integer.valueOf(RfbClient.Keysym.LEFT);
            case "RIGHT":
                return Integer.valueOf(RfbClient.Keysym.RIGHT);
            case "HOME":
                return Integer.valueOf(RfbClient.Keysym.HOME);
            case "END":
                return Integer.valueOf(RfbClient.Keysym.END);
            case "PAGEUP":
                return Integer.valueOf(RfbClient.Keysym.PAGE_UP);
            case "PAGEDOWN":
                return Integer.valueOf(RfbClient.Keysym.PAGE_DOWN);
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            List<String> options = new ArrayList<String>();
            for (String name : SPECIAL_NAMES) {
                options.add("@" + name);
            }
            return options;
        }
        return Arrays.asList();
    }
}
