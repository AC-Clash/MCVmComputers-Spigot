package me.acclashcorporation.project_magisha.commands;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import me.acclashcorporation.project_magisha.Project_Magisha;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationTargetException;

public class ArmSwingTest implements CommandExecutor {

    private ProtocolManager protocolManager;


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        protocolManager = ProtocolLibrary.getProtocolManager();

        if (sender instanceof Player) {

            Player player = (Player) sender;

            PacketContainer armswigtest = new PacketContainer(PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE);
            armswigtest.getIntegers().write(0, 999999999);


            try {
                protocolManager.sendServerPacket(player, armswigtest);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }


            player.getWorld().getWorldBorder().setWarningDistance(5);
        }



        return true;
    }
}
