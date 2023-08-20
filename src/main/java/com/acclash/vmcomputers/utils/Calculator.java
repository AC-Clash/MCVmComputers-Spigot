package com.acclash.vmcomputers.utils;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public class Calculator {

    public static class Tuple<BlockFace, Integer> {
        private final BlockFace blockFace;
        private final Integer integer;

        public Tuple(BlockFace first, Integer second) {
            this.blockFace = first;
            this.integer = second;
        }

        public BlockFace getBlockFace() {
            return blockFace;
        }

        public Integer getInteger() {
            return integer;
        }
    }

    public static Tuple<BlockFace, Integer> getMonitorBlockFaceAndYaw(float yaw) {
        BlockFace blockFace;
        int roundedYaw;

        if (yaw >= 45.1 && yaw <= 135) {
            blockFace = BlockFace.WEST;
            roundedYaw = 90;
        } else if (yaw <= 45.0 && yaw >= -44.9) {
            blockFace = BlockFace.SOUTH;
            roundedYaw = 0;
        } else if (yaw <= -45.0 && yaw >= -134.9) {
            blockFace = BlockFace.EAST;
            roundedYaw = -90;
        } else {
            blockFace = BlockFace.NORTH;
            roundedYaw = 180;
        }

        return new Tuple<>(blockFace, roundedYaw);
    }

    public static Location locationWithOffset(Location location, double offsetX, double offsetY, double offsetZ) {
        return new Location(location.getWorld(), location.getX() + offsetX, location.getY() + offsetY, location.getZ() + offsetZ);
    }

    public static Location calculateEChairLoc(Location location, BlockFace blockFace) {
        Location resultLocation;
        switch (blockFace) {
            case NORTH:
                resultLocation = locationWithOffset(location, 0.5, 0, 0.33);
                break;
            case WEST:
                resultLocation = locationWithOffset(location, 0.33, 0, 0.5);
                break;
            case SOUTH:
                resultLocation = locationWithOffset(location, 0.5, 0, 0.66);
                break;
            default:
                resultLocation = locationWithOffset(location, 0.66, 0, 0.5);
        }
        return resultLocation;
    }

}
