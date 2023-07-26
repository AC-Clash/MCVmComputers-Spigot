package com.acclash.magishacomputerdivision.utils;

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
        return switch (blockFace) {
            case NORTH -> locationWithOffset(location, 0.5, 0, 0.33);
            case WEST -> locationWithOffset(location, 0.33, 0, 0.5);
            case SOUTH -> locationWithOffset(location, 0.5, 0, 0.66);
            default -> locationWithOffset(location, 0.66, 0, 0.5);
        };
    }

// Do the same for calculateMonitorLoc, calculateButtonLoc, calculateTowerLoc, calculateKeyboardLoc, and areaCheck methods

    public static Location calculateMonitorLoc(Location location, BlockFace blockFace) {
        return switch (blockFace) {
            case SOUTH -> locationWithOffset(location, 0, 1, -1);
            case EAST -> locationWithOffset(location, -1, 1, 0);
            case NORTH -> locationWithOffset(location, 0, 1, 1);
            default -> locationWithOffset(location, 1, 1, 0.5);
        };
    }

    public static Location calculateButtonLoc(Location location, BlockFace blockFace) {
        return switch (blockFace) {
            case SOUTH -> locationWithOffset(location, -1, 1, 1);
            case EAST -> locationWithOffset(location, 1, 1, 1);
            case NORTH -> locationWithOffset(location, 1, 1, -1);
            default -> locationWithOffset(location, -1, 1, -1);
        };
    }

    public static Location calculateTowerLoc(Location location, BlockFace blockFace) {
        return switch (blockFace) {
            case SOUTH -> locationWithOffset(location, 1, 1, 1);
            case EAST -> locationWithOffset(location, 1, 1, -1);
            case NORTH -> locationWithOffset(location, -1, 1, -1);
            default -> locationWithOffset(location, -1, 1, 1);
        };
    }

    public static Location calculateKeyboardLoc(Location location, BlockFace blockFace) {
        return switch (blockFace) {
            case SOUTH -> locationWithOffset(location, 0, 1, 1);
            case EAST -> locationWithOffset(location, 1, 1, 0);
            case NORTH -> locationWithOffset(location, 0, 1, -1);
            default -> locationWithOffset(location, -1, 1, 0);
        };
    }

    public static Location areaCheck(Location location, BlockFace blockFace) {
        return switch (blockFace) {
            case SOUTH -> locationWithOffset(location, 0, 1, -2);
            case EAST -> locationWithOffset(location, -2, 1, 0);
            case NORTH -> locationWithOffset(location, 0, 1, 2);
            default -> locationWithOffset(location, 2, 1, 0);
        };
    }

}
