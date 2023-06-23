package com.acclash.projectmagisha.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class FollowPlayerGoal extends MoveToBlockGoal {

    private final org.bukkit.entity.Player player;

    public FollowPlayerGoal(PathfinderMob pathfindermob, double speedModifier, Player player) {
        super(pathfindermob, speedModifier, 0);
        this.player = player;
    }

    @Override
    protected boolean findNearestBlock() {
        return true;
    }

    @Override
    public void tick() {
        net.minecraft.world.entity.player.Player nmsPlayer = ((CraftPlayer) player).getHandle();
        this.blockPos = nmsPlayer.getOnPos();
        this.mob.getNavigation().moveTo((double)((float)nmsPlayer.getX()) + 0.5, nmsPlayer.getY(), (double)((float)nmsPlayer.getZ()) + 0.5, this.speedModifier);
        super.tick();
    }

    @Override
    public double acceptedDistance() {
        return 2.0;
    }

    @Override
    protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
        return true;
    }
}