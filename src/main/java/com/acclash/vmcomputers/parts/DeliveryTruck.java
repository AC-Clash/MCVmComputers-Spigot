package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An order arriving the long way round: a truck pulls up, Steve gets out, takes a box from the
 * back, hands it over, and drives off.
 *
 * <h2>What this costs</h2>
 *
 * Almost nothing, which is worth stating because it looks expensive. The truck is 22 display
 * entities moved by teleporting them every {@link #MOVE_STEP} ticks with a matching
 * {@link Display#setTeleportDuration}, which makes the <em>client</em> interpolate between the
 * positions -- so smooth motion costs one short packet per entity per step, not one per tick. At
 * two-tick steps that is 220 packets a second, on the order of 6 KB/s per viewer, for the nine
 * seconds the truck exists.
 *
 * <p>For scale: one map panel update is 16,400 bytes, and a running computer sends those at up to
 * twenty a second. The entire delivery costs less than two panel updates. Nothing here is worth
 * optimising against the screen.
 *
 * <h2>Why the schedule is fixed</h2>
 *
 * Every beat below happens on a tick count, and in particular the handover happens on
 * {@link #HANDOVER} whether or not Steve got there. He is a real villager walked by velocity, so a
 * fence post, a one-block step or a player standing in a doorway can stop him; if the delivery
 * waited on his arrival, getting stuck would mean losing parts that were already paid for. The
 * parts arrive on time and Steve is pure theatre.
 *
 * <p>The same reasoning covers the player leaving: log out, die, or walk into a nether portal
 * mid-delivery and the box is dropped at once at the last place they were, rather than the run
 * being abandoned with the money already taken.
 */
public final class DeliveryTruck {

    private static final String MODEL = "delivery_truck";

    /** Owner id for truck scenery, distinct from a computer's and from a package's. */
    private static final int TRUCK_OWNER = -3;

    /** Tags every entity a delivery spawns, so orphans can be swept without knowing the run. */
    private static final String RUN_KEY = "vmcDelivery";

    /**
     * Distance from the truck's centre back to where Steve stands at the rear doors.
     *
     * <p>The cargo box ends 2.25 blocks behind centre in the model, so three leaves him standing
     * clear of the doors rather than inside them.
     */
    public static final double REAR_OFFSET = 3.0;

    // The schedule, in ticks from dispatch. Roughly nine seconds end to end.
    private static final int APPROACH_END = 40;
    private static final int STEVE_OUT = 48;
    private static final int AT_REAR = 68;
    private static final int BOX_OUT = 72;
    private static final int AT_PLAYER = 102;
    private static final int HANDOVER = 106;
    private static final int BACK_AT_CAB = 136;
    private static final int STEVE_IN = 140;
    private static final int DEPART_START = 144;
    private static final int DEPART_END = 184;

    /** Ticks between truck teleports. The client tweens the gap, so this is smoothness per byte. */
    private static final int MOVE_STEP = 2;

    /** Blocks per tick. A shade over four blocks a second, which is a brisk walk. */
    private static final double WALK_SPEED = 0.22;

    /** One delivery per player. A second order while one is in flight just joins the box. */
    private static final Map<UUID, DeliveryTruck> running =
            new ConcurrentHashMap<UUID, DeliveryTruck>();

    private final UUID owner;
    private final World world;
    private final List<ComponentType> contents;
    private final Roadway road;
    private final BlockFace heading;

    /** Everything spawned, in spawn order, so teardown is exact rather than by proximity. */
    private final List<Entity> spawned = new ArrayList<Entity>();
    private final List<Entity> truckParts = new ArrayList<Entity>();
    private final List<Entity> carriedBox = new ArrayList<Entity>();

    private Villager steve;
    private BukkitTask task;
    private int tick;
    private boolean handedOver;
    /** Where the truck was last put, so the engine is heard from the truck and not the kerb. */
    private double truckIndex;

    private final Location cabDoor;
    private final Location rearSpot;
    private final Location handoverSpot;

    private DeliveryTruck(Player player, List<ComponentType> contents, Roadway road) {
        this.owner = player.getUniqueId();
        this.world = player.getWorld();
        // Copied because a second order in flight is appended to it, and the caller's list is not
        // ours to grow.
        this.contents = new ArrayList<ComponentType>(contents);
        this.road = road;
        this.heading = road.heading();

        Location park = road.at(road.parkIndex());
        BlockFace side = rightOf(heading);

        // Which flank the player is on, so Steve steps out of the near door instead of walking
        // through the truck. Positive means they are on the model's +X side.
        Vector toPlayer = player.getLocation().toVector().subtract(park.toVector());
        double sign = toPlayer.getX() * side.getModX() + toPlayer.getZ() * side.getModZ() >= 0
                ? 1.0 : -1.0;

        // Model space: the cab is 1.45 forward of centre and its side is 0.95 out.
        this.cabDoor = park.clone().add(
                heading.getModX() * 1.45 + side.getModX() * 1.6 * sign,
                0,
                heading.getModZ() * 1.45 + side.getModZ() * 1.6 * sign);
        this.rearSpot = road.rearOfParkedTruck();

        Location feet = player.getLocation();
        Vector fromPlayer = rearSpot.toVector().subtract(feet.toVector()).setY(0);
        this.handoverSpot = fromPlayer.lengthSquared() < 1.0e-6
                ? feet.clone()
                : feet.clone().add(fromPlayer.normalize().multiply(1.6));
    }

    /**
     * Sends a truck if there is anywhere for one to drive.
     *
     * @return false if there is no road, in which case the caller should deliver the plain way
     */
    public static boolean dispatch(Player player, List<ComponentType> contents) {
        // A second order while one is on the road goes on the same truck, rather than declining
        // and telling the player there is no room to drive when a truck is visibly outside.
        DeliveryTruck inFlight = running.get(player.getUniqueId());
        if (inFlight != null) {
            return inFlight.alsoCarry(player, contents);
        }
        if (PartModels.get(MODEL) == null) {
            return false;
        }
        Roadway road = Roadway.find(player);
        if (road == null) {
            return false;
        }

        DeliveryTruck delivery = new DeliveryTruck(player, contents, road);
        running.put(player.getUniqueId(), delivery);
        delivery.begin(player);
        return true;
    }

    /**
     * Adds to an order already on the truck.
     *
     * @return false once the box has left Steve's hands, when the caller must deliver separately
     */
    private boolean alsoCarry(Player player, List<ComponentType> extra) {
        if (handedOver) {
            return false;
        }
        contents.addAll(extra);
        player.sendMessage(ChatColor.GOLD + "Steve: " + ChatColor.WHITE
                + "GOOD NEWS, IT'S ON THE SAME TRUCK.");
        return true;
    }

    /** Ends every delivery in flight, handing over first. For plugin shutdown and reload. */
    public static void stopAll() {
        for (DeliveryTruck delivery : new ArrayList<DeliveryTruck>(running.values())) {
            delivery.cutShort();
        }
        running.clear();
    }

    /**
     * Removes delivery entities left behind by a crash.
     *
     * <p>The entities are spawned non-persistent, so a hard stop loses them on its own; this is
     * for {@code /reload}, which does not unload chunks, and for anything that gets saved anyway.
     *
     * @return how many were removed
     */
    public static int sweep() {
        NamespacedKey key = runKey();
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private void begin(Player player) {
        player.sendMessage(ChatColor.GOLD + "Steve: " + ChatColor.WHITE
                + "I'M PULLING UP NOW. DON'T MOVE.");

        Location start = road.at(0);
        Quaternionf turn = new Quaternionf().rotateY(PartRenderer.yawFor(heading));

        for (BlockDisplay display : PartRenderer.spawnNamedDisplays(
                start, heading, MODEL, 1.0f, TRUCK_OWNER)) {
            // Parts are set to a short view range because they are small and there are many. A
            // truck is neither, and one that pops in at eight blocks is worse than no truck.
            display.setViewRange(1.4f);
            display.setTeleportDuration(MOVE_STEP);
            display.setPersistent(false);
            truckParts.add(remember(display));
        }

        // Lettering on both flanks, proud of the livery stripe and centred on the cargo box.
        for (boolean east : new boolean[]{true, false}) {
            TextDisplay sign = Branding.sign(start, Branding.COMPANY,
                    new Vector3f(east ? 1.07f : -1.07f, 1.85f, 0.8f), turn, east, 1.0f);
            sign.setViewRange(1.4f);
            sign.setTeleportDuration(MOVE_STEP);
            sign.setPersistent(false);
            truckParts.add(remember(sign));
        }

        this.task = Bukkit.getScheduler().runTaskTimer(
                VMComputers.getPlugin(), this::tick, 0L, 1L);
    }

    private void tick() {
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline() || !player.getWorld().equals(world)) {
            // Gone. Leave the box where they were rather than swallowing a paid order.
            cutShort();
            return;
        }

        tick++;

        if (tick <= APPROACH_END) {
            // Ease out, so it rolls to a stop instead of stopping dead.
            double t = tick / (double) APPROACH_END;
            moveTruck(road.parkIndex() * (1.0 - (1.0 - t) * (1.0 - t)));
            engine();
        } else if (tick == APPROACH_END + 1) {
            world.playSound(road.at(road.parkIndex()), Sound.BLOCK_PISTON_CONTRACT, 0.7f, 0.6f);
        } else if (tick == STEVE_OUT) {
            world.playSound(cabDoor, Sound.BLOCK_IRON_DOOR_OPEN, 0.7f, 1.1f);
            spawnSteve();
        } else if (tick > STEVE_OUT && tick <= AT_REAR) {
            walk(rearSpot, AT_REAR - tick);
        } else if (tick == BOX_OUT) {
            world.playSound(rearSpot, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 0.9f);
            takeOutBox(player);
        } else if (tick > BOX_OUT && tick <= AT_PLAYER) {
            walk(handoverSpot, AT_PLAYER - tick);
            carryBox();
        } else if (tick > AT_PLAYER && tick < HANDOVER) {
            face(player.getLocation());
            carryBox();
        } else if (tick == HANDOVER) {
            handOver(player);
        } else if (tick > HANDOVER && tick <= BACK_AT_CAB) {
            walk(cabDoor, BACK_AT_CAB - tick);
        } else if (tick == STEVE_IN) {
            world.playSound(cabDoor, Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 1.1f);
            removeSteve();
        } else if (tick >= DEPART_START && tick <= DEPART_END) {
            // Ease in: it pulls away rather than jumping to speed.
            double t = (tick - DEPART_START) / (double) (DEPART_END - DEPART_START);
            int park = road.parkIndex();
            moveTruck(park + (road.lastIndex() - park) * t * t);
            engine();
        }

        // One tick past the end, so the last teleport of the drive-off is actually seen before
        // everything is removed.
        if (tick > DEPART_END) {
            finish();
        }
    }

    /**
     * Teleports the whole truck to a point on the lane.
     *
     * <p>Only every {@link #MOVE_STEP} ticks: the displays carry a matching teleport duration, so
     * the client interpolates across the gap and the motion is smooth without a packet per tick.
     */
    private void moveTruck(double index) {
        truckIndex = index;
        if (tick % MOVE_STEP != 0) {
            return;
        }
        Location where = road.at(index);
        for (Entity part : truckParts) {
            if (part.isValid()) {
                part.teleport(where);
            }
        }
    }

    private void engine() {
        if (tick % 6 == 0) {
            world.playSound(road.at(truckIndex), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.75f);
        }
    }

    private void spawnSteve() {
        Location at = cabDoor.clone();
        at.setYaw(yawTowards(rearSpot.toVector().subtract(at.toVector())));
        steve = world.spawn(at, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.TOOLSMITH);
            villager.setVillagerType(Villager.Type.PLAINS);
            villager.setAdult();
            villager.setCustomName(ChatColor.GOLD + "Steve");
            villager.setCustomNameVisible(true);
            // Aware, not AI: an unaware mob keeps its physics -- gravity, step height and the walk
            // animation that comes from actually moving -- while ignoring every goal it has. With
            // AI off entirely he would slide along like a statue; with AI on he would wander off
            // to a bed. Neither is a courier.
            villager.setAware(false);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setRemoveWhenFarAway(false);
            villager.setPersistent(false);
        });
        remember(steve);
        world.playSound(at, Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.0f);
    }

    /**
     * Pushes Steve towards a spot so that he arrives in {@code ticksLeft}.
     *
     * <p>Recomputed from his real position every tick rather than set once, so friction, a slope
     * or a shove all correct themselves; and clamped, so a long way to go becomes a late arrival
     * rather than a villager fired across the garden.
     */
    private void walk(Location target, int ticksLeft) {
        if (steve == null || !steve.isValid()) {
            return;
        }
        Vector delta = target.toVector().subtract(steve.getLocation().toVector()).setY(0);
        double distance = delta.length();
        if (distance < 0.08) {
            return;
        }

        double perTick = Math.min(distance / Math.max(ticksLeft, 1), WALK_SPEED);
        Vector step = delta.normalize().multiply(perTick);
        steve.setVelocity(new Vector(step.getX(), steve.getVelocity().getY(), step.getZ()));
        steve.setRotation(yawTowards(delta), 0f);
    }

    private void face(Location target) {
        if (steve != null && steve.isValid()) {
            steve.setRotation(
                    yawTowards(target.toVector().subtract(steve.getLocation().toVector())), 0f);
        }
    }

    /** Spawns the box Steve carries: scenery only, with the real package created at handover. */
    private void takeOutBox(Player player) {
        BlockFace carryFacing = towards(rearSpot, player.getLocation());
        Quaternionf turn = new Quaternionf().rotateY(PartRenderer.yawFor(carryFacing));

        for (BlockDisplay display : PartRenderer.spawnNamedDisplays(
                carriedAt(), carryFacing, "package", 1.0f, TRUCK_OWNER)) {
            display.setTeleportDuration(1);
            display.setPersistent(false);
            carriedBox.add(remember(display));
        }
        for (boolean east : new boolean[]{true, false}) {
            TextDisplay sign = Branding.sign(carriedAt(), Branding.COMPANY_SHORT,
                    new Vector3f(east ? 0.26f : -0.26f, 0.24f, 0f), turn, east, 0.5f);
            sign.setTeleportDuration(1);
            sign.setPersistent(false);
            carriedBox.add(remember(sign));
        }
    }

    private void carryBox() {
        if (steve == null || !steve.isValid()) {
            return;
        }
        Location at = carriedAt();
        for (Entity piece : carriedBox) {
            if (piece.isValid()) {
                piece.teleport(at);
            }
        }
    }

    /** Held out in front of him at chest height. */
    private Location carriedAt() {
        if (steve == null || !steve.isValid()) {
            return rearSpot.clone().add(0, 0.9, 0);
        }
        Location at = steve.getLocation();
        Vector forward = at.getDirection().setY(0);
        if (forward.lengthSquared() > 1.0e-6) {
            forward.normalize().multiply(0.32);
        }
        return at.clone().add(forward.getX(), 0.85, forward.getZ());
    }

    private void handOver(Player player) {
        clearCarriedBox();
        handedOver = true;
        Delivery.send(player, contents);
        world.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.0f);
        player.sendMessage(ChatColor.GOLD + "Steve: " + ChatColor.WHITE + "THERE YOU GO. "
                + "SIGN NOTHING, I'M IN A HURRY.");
        player.sendMessage(ChatColor.GRAY + "Right-click the box to open it.");
    }

    /**
     * Ends early: the player left, or the server is going down. The order is still delivered,
     * because it has already been paid for.
     */
    private void cutShort() {
        if (!handedOver) {
            handedOver = true;
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline() && player.getWorld().equals(world)) {
                Delivery.send(player, contents);
            } else {
                Delivery.dropAt(handoverSpot, owner, heading, contents);
            }
        }
        finish();
    }

    private void finish() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearCarriedBox();
        removeSteve();
        for (Entity entity : spawned) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        spawned.clear();
        truckParts.clear();
        running.remove(owner, this);
    }

    private void clearCarriedBox() {
        for (Entity piece : carriedBox) {
            if (piece.isValid()) {
                piece.remove();
            }
            spawned.remove(piece);
        }
        carriedBox.clear();
    }

    private void removeSteve() {
        if (steve != null) {
            if (steve.isValid()) {
                steve.remove();
            }
            spawned.remove(steve);
            steve = null;
        }
    }

    /** Tags an entity as delivery scenery and adds it to the teardown list. */
    private <T extends Entity> T remember(T entity) {
        entity.getPersistentDataContainer().set(runKey(), PersistentDataType.STRING, "true");
        spawned.add(entity);
        return entity;
    }

    /** Minecraft's entity yaw, where zero is south. Not the same convention as PartRenderer. */
    private static float yawTowards(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    /** The cardinal direction from one point to another, for turning a model. */
    private static BlockFace towards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

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

    private static NamespacedKey runKey() {
        return new NamespacedKey(VMComputers.getPlugin(), RUN_KEY);
    }
}
