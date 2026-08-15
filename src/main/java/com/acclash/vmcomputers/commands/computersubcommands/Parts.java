package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.ComputerLayout;
import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.parts.Furniture;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.PartModel;
import com.acclash.vmcomputers.parts.PartModels;
import com.acclash.vmcomputers.parts.PartRenderer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Previews component models without building a computer.
 *
 * <p>These parts are assembled from vanilla blocks rather than drawn from a texture, so how close
 * one looks to the mod's original is a judgement call that can only be made by standing in front
 * of it. Building a whole computer to check one keyboard is too slow a loop for that, and tuning
 * the block choices in {@code tools/generate_parts.py} needs the loop to be fast.
 *
 * <p>Previews are tagged to computer id {@link #PREVIEW_ID}, which no real computer can have, so
 * {@code clear} can remove every preview without touching a built machine.
 */
public class Parts extends ComputerSubCommand {

    /** Owner id for preview entities. Real computers come from a database sequence and start at 1. */
    private static final int PREVIEW_ID = -1;

    private static final double CLEAR_RADIUS = 32.0;

    @Override
    public String getName() {
        return "parts";
    }

    @Override
    public String getDescription() {
        return "Previews a component model where you are standing.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers parts <model> [scale]"
                + ChatColor.GRAY + ", /vmcomputers parts list"
                + ChatColor.GRAY + ", /vmcomputers parts clear";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (!PartModels.isLoaded()) {
            player.sendMessage(ChatColor.RED
                    + "No part models are loaded; check the console for a parts.json error.");
            return;
        }

        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            listModels(player);
            return;
        }

        if (args[1].equalsIgnoreCase("clear")) {
            int removed = PartRenderer.despawn(player.getLocation(), CLEAR_RADIUS, PREVIEW_ID);
            player.sendMessage(ChatColor.GREEN + "Removed " + removed + " preview piece(s) within "
                    + (int) CLEAR_RADIUS + " blocks.");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);

        // The desk is generated per computer rather than loaded from parts.json, so it is not in
        // PartModels. Previewing it needs a size to build against; LARGE is the one the geometry
        // was tuned for.
        PartModel model = name.equals("desk")
                ? Furniture.desk(ComputerLayout.of(MonitorSize.LARGE))
                : PartModels.get(name);
        if (model == null) {
            player.sendMessage(ChatColor.RED + "No model named '" + args[1] + "'.");
            player.sendMessage(ChatColor.GRAY + "Try /vmcomputers parts list");
            return;
        }

        float scale = 1.0f;
        if (args.length >= 3) {
            try {
                scale = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "'" + args[2] + "' is not a number.");
                return;
            }
            if (!(scale > 0f) || scale > 16f) {
                player.sendMessage(ChatColor.RED + "Scale must be between 0 and 16.");
                return;
            }
        }

        // Placed on the block the player is standing on, turned to look back at them, which is how
        // a part on a desk is actually seen.
        Location spot = player.getLocation();
        spot = new Location(spot.getWorld(), Math.floor(spot.getX()) + 0.5,
                Math.floor(spot.getY()), Math.floor(spot.getZ()) + 0.5);

        BlockFace facing = player.getFacing();
        List<?> spawned = PartRenderer.spawn(spot, facing, model, scale, PREVIEW_ID);

        player.sendMessage(ChatColor.GREEN + "Previewing " + ChatColor.WHITE + model.name()
                + ChatColor.GREEN + " - " + spawned.size() + " display piece(s), scale " + scale
                + ", facing " + facing.name().toLowerCase(Locale.ROOT) + ".");
        player.sendMessage(ChatColor.GRAY + "/vmcomputers parts clear removes previews nearby.");
    }

    private void listModels(Player player) {
        player.sendMessage(getSyntax());
        List<String> names = PartModels.names();
        player.sendMessage(ChatColor.GRAY + "" + names.size() + " models:");

        StringBuilder line = new StringBuilder();
        for (String name : names) {
            PartModel model = PartModels.get(name);
            String entry = name + ChatColor.DARK_GRAY + "(" + model.pieceCount() + ")"
                    + ChatColor.GRAY;
            if (line.length() + entry.length() > 90) {
                player.sendMessage(ChatColor.GRAY + "  " + line);
                line.setLength(0);
            }
            line.append(line.length() == 0 ? "" : ", ").append(entry);
        }
        if (line.length() > 0) {
            player.sendMessage(ChatColor.GRAY + "  " + line);
        }

        // The catalogue names are what the ordering GUI will show; the model names above are what
        // draws them. They are not the same list, so print both rather than imply they are.
        player.sendMessage(ChatColor.GRAY + "Catalogue: " + ComponentType.all().size()
                + " orderable components.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> options = new ArrayList<String>(PartModels.names());
            options.add("desk");
            options.add("list");
            options.add("clear");
            return options;
        }
        return null;
    }
}
