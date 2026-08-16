package com.acclash.vmcomputers.parts;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stretch of ground a truck can drive along, found in the world near a player.
 *
 * <p>This is the part of a delivered order that can fail, and it is worth being clear about why:
 * the animation itself is cheap and reliable, but a truck needs somewhere to drive, and players
 * order parts from caves, from three-by-three huts, from the top of mob farms and from boats. So
 * the road is looked for first and the whole cinematic is skipped when there isn't one, rather
 * than driving a two-block-wide van through a wall.
 *
 * <h2>What counts as drivable</h2>
 *
 * A lane is a straight run of columns along one cardinal direction, laid a few blocks to the side
 * of the player. Each column needs solid ground and {@link #CLEARANCE} blocks of air above it, and
 * the two columns either side of it need the air but not the ground -- the truck is two blocks
 * wide inside a three-wide corridor, so the outer half-blocks are only margin.
 *
 * <p>Ground level is allowed to step by one block between neighbouring columns and the lane
 * follows it. Requiring a perfectly flat sixteen-block run would fail on most natural terrain;
 * following a gentle slope costs nothing and looks like a truck driving up a hill.
 *
 * <p>Every block read is guarded by a loaded-chunk check. An unguarded {@code getBlockAt} loads
 * the chunk synchronously, and this runs on the server thread the instant a player clicks Order.
 */
public final class Roadway {

    /** Air needed above the road. The truck is 2.56 blocks tall. */
    private static final int CLEARANCE = 3;

    /** How far the lane is searched either side of the parking spot. */
    private static final int LANE_HALF = 9;

    /** How much road is needed on each side of the parking spot for the drive to read as one. */
    private static final int MIN_RUN = 7;

    /** How far the ground may be above or below the player's feet and still be the same road. */
    private static final int Y_WINDOW = 2;

    /**
     * How far to the side of the player the truck parks.
     *
     * <p>Both signs of both distances are tried. Three blocks is close enough that Steve's walk is
     * short and far enough that a 2.0-wide truck is not parked on top of the player.
     */
    private static final int[] LATERALS = {3, -3, 4, -4};

    private final List<Location> lane;
    private final int parkIndex;
    private final BlockFace heading;

    private Roadway(List<Location> lane, int parkIndex, BlockFace heading) {
        this.lane = Collections.unmodifiableList(lane);
        this.parkIndex = parkIndex;
        this.heading = heading;
    }

    /** Where along the lane the truck stops. */
    public int parkIndex() {
        return parkIndex;
    }

    /** The last usable index; the truck drives off the end of the lane and despawns there. */
    public int lastIndex() {
        return lane.size() - 1;
    }

    /** The direction of travel. The truck model is turned to face this way. */
    public BlockFace heading() {
        return heading;
    }

    /**
     * A point along the lane, by fractional index.
     *
     * <p>Columns are one block apart horizontally, so index is distance to within the small error
     * a one-block slope introduces -- close enough that easing by index reads as easing by speed.
     */
    public Location at(double index) {
        double clamped = Math.max(0.0, Math.min(index, lane.size() - 1.0));
        int low = (int) Math.floor(clamped);
        int high = Math.min(low + 1, lane.size() - 1);
        double t = clamped - low;

        Location a = lane.get(low);
        Location b = lane.get(high);
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t);
    }

    /**
     * How much open air stands above a lane column, up to {@code max}.
     *
     * <p>The truck flies in, so the lane is a landing strip rather than a road, and the descent
     * needs room above the far end of it. This measures what is actually there instead of
     * demanding a fixed amount: a lane under a canopy still gets a delivery, just a shallower
     * approach. Rejecting it outright would fail on any road with a tree beside it.
     */
    public int clearanceAbove(int index, int max) {
        Location at = at(index);
        World world = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return 0;
        }
        for (int dy = 0; dy < max; dy++) {
            Block block = world.getBlockAt(x, y + dy, z);
            if (!block.isPassable() || block.isLiquid()) {
                return dy;
            }
        }
        return max;
    }

    /** Where the truck's rear doors end up once it has parked. */
    public Location rearOfParkedTruck() {
        Location park = at(parkIndex);
        return park.clone().add(
                -heading.getModX() * DeliveryTruck.REAR_OFFSET,
                0,
                -heading.getModZ() * DeliveryTruck.REAR_OFFSET);
    }

    /**
     * Looks for a road near a player.
     *
     * @return a lane the truck can use, or null if there is nowhere to drive
     */
    public static Roadway find(Player player) {
        World world = player.getWorld();
        Location feet = player.getLocation();
        int px = feet.getBlockX();
        int py = feet.getBlockY();
        int pz = feet.getBlockZ();
        BlockFace looking = player.getFacing();

        Roadway best = null;
        int bestScore = Integer.MIN_VALUE;

        // Nothing can beat a full-length lane that crosses the player's view at three blocks, so
        // finding one ends the search. On open ground that is usually the first or second try,
        // which matters: this runs on the server thread the moment a player clicks Order, and the
        // exhaustive version is about fifteen thousand block reads.
        final int perfect = LANE_HALF * 4 + 3 + 1;

        for (BlockFace heading : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            BlockFace side = rightOf(heading);
            for (int lateral : LATERALS) {
                int parkX = px + side.getModX() * lateral;
                int parkZ = pz + side.getModZ() * lateral;

                Integer parkY = floorAt(world, parkX, parkZ, py, side);
                if (parkY == null) {
                    continue;
                }

                List<Location> back = run(world, parkX, parkZ, parkY.intValue(),
                        opposite(heading), side);
                List<Location> forward = run(world, parkX, parkZ, parkY.intValue(),
                        heading, side);
                if (back.size() < MIN_RUN || forward.size() < MIN_RUN) {
                    continue;
                }

                // A truck that crosses the player's view is worth more than one that drives at
                // them or away from them, which is mostly foreshortening and hard to read.
                boolean across = side == looking || side == opposite(looking);
                int score = Math.min(back.size(), forward.size()) * 4
                        + (across ? 3 : 0)
                        + (Math.abs(lateral) == 3 ? 1 : 0);
                if (score <= bestScore) {
                    continue;
                }

                List<Location> lane = new ArrayList<Location>(back.size() + 1 + forward.size());
                for (int i = back.size() - 1; i >= 0; i--) {
                    lane.add(back.get(i));
                }
                int parkIndex = lane.size();
                lane.add(centre(world, parkX, parkY.intValue(), parkZ));
                lane.addAll(forward);

                best = new Roadway(lane, parkIndex, heading);
                bestScore = score;
                if (bestScore >= perfect) {
                    return best;
                }
            }
        }
        return best;
    }

    /**
     * Walks outward from the parking spot, collecting drivable columns until the road runs out.
     *
     * <p>Ground level carries forward from the previous column rather than being re-derived from
     * the player, so a lane can climb steadily instead of being held to a band around wherever the
     * player happens to be standing.
     */
    private static List<Location> run(World world, int fromX, int fromZ, int fromY,
                                      BlockFace direction, BlockFace side) {
        List<Location> found = new ArrayList<Location>(LANE_HALF);
        int y = fromY;
        for (int step = 1; step <= LANE_HALF; step++) {
            int x = fromX + direction.getModX() * step;
            int z = fromZ + direction.getModZ() * step;
            Integer next = floorAt(world, x, z, y, side);
            // One block of step between neighbours; anything more is a cliff or a wall, not a road.
            if (next == null || Math.abs(next.intValue() - y) > 1) {
                break;
            }
            y = next.intValue();
            found.add(centre(world, x, y, z));
        }
        return found;
    }

    /**
     * The height a truck would sit at in this column, or null if it cannot drive here.
     *
     * @param nearY ground is looked for within {@link #Y_WINDOW} of this
     * @param side  the lane's lateral direction, for the width check
     */
    private static Integer floorAt(World world, int x, int z, int nearY, BlockFace side) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        // Highest first, so a road on top of a hill beats the cave underneath it.
        for (int y = nearY + Y_WINDOW; y >= nearY - Y_WINDOW; y--) {
            if (!world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                continue;
            }
            if (!clear(world, x, y, z)) {
                continue;
            }
            // The truck is wider than one block; the columns it overhangs need the headroom even
            // though they do not need the ground.
            if (!clear(world, x + side.getModX(), y, z + side.getModZ())
                    || !clear(world, x - side.getModX(), y, z - side.getModZ())) {
                continue;
            }
            return Integer.valueOf(y);
        }
        return null;
    }

    /** True if a column has the truck's headroom free. */
    private static boolean clear(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        for (int dy = 0; dy < CLEARANCE; dy++) {
            Block block = world.getBlockAt(x, y + dy, z);
            // Liquids are passable, which is not the same as drivable.
            if (!block.isPassable() || block.isLiquid()) {
                return false;
            }
        }
        return true;
    }

    private static Location centre(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /** The face 90 degrees clockwise of this one, looking down. */
    private static BlockFace rightOf(BlockFace face) {
        switch (face) {
            case NORTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.WEST;
            default:
                return BlockFace.NORTH;
        }
    }

    private static BlockFace opposite(BlockFace face) {
        return rightOf(rightOf(face));
    }
}
