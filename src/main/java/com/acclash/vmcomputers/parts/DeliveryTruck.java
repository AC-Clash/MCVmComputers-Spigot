package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An order arriving the long way round: a truck flies in on ducted fans, holds a hover over the
 * spot, settles straight down, and Steve gets out and hands over a box before it lifts off
 * vertically and climbs away.
 *
 * <h2>What this costs</h2>
 *
 * Almost nothing, which is worth stating because it looks expensive. The truck is 30 display
 * entities moved by teleporting them every {@link #MOVE_STEP} ticks with a matching
 * {@link Display#setTeleportDuration}, which makes the <em>client</em> interpolate between the
 * positions -- so smooth motion costs one short packet per entity per step, not one per tick. With
 * the fan downwash and the spinning blades on top, the whole thing is on the order of 12 KB/s per
 * viewer for the ten seconds it exists.
 *
 * <p>For scale: one map panel update is 16,400 bytes, and a running computer sends those at up to
 * twenty a second. A whole delivery costs less than a second of one screen. Nothing here is worth
 * optimising against the screen.
 *
 * <h2>Moving parts</h2>
 *
 * The landing gear and the fan blades are the same trick as the truck's motion, one level down: a
 * display interpolates towards whatever transformation it is given, so an animation is two poses
 * and a duration. The wheels have their stowed pose authored in {@code vehicles.json} beside their
 * deployed one ({@link PartModel.Piece#folded}), and the blades are simply handed a new angle
 * every few ticks. Neither costs a tick of server work while it plays.
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

    /** How high it comes in and hovers, headroom permitting. */
    private static final int MAX_ALTITUDE = 9;

    /**
     * Extra height it will gain on the way out, on top of the hover height.
     *
     * <p>Without this the climb-out is level: both ends of the strip measure the same nine blocks
     * of open sky, so the departure has nowhere left to climb to and "forward and up" comes out as
     * just forward. The far end is re-measured with this added, which is safe because it is only
     * used where the truck is leaving and about to despawn.
     */
    private static final int CLIMB_OUT = 6;

    /** How far the hover drifts up and down while it holds station over the pad. */
    private static final double HOVER_BOB = 0.12;

    // The schedule, in ticks from dispatch. Roughly twelve seconds end to end.
    //
    // It flies like a helicopter rather than a plane: in level, stop over the pad, hold, then
    // straight down. Leaving is the same in reverse -- straight up off the ground first, and only
    // then away. Nothing about that is more expensive than the diagonal it replaced; it is the
    // same two numbers per tick, eased differently.
    private static final int INBOUND_END = 30;
    private static final int GEAR_DOWN = 26;
    private static final int HOVER_END = 42;
    private static final int DESCENT_END = 68;
    private static final int STEVE_OUT = 76;
    private static final int AT_REAR = 96;
    private static final int BOX_OUT = 100;
    private static final int AT_PLAYER = 130;
    private static final int HANDOVER = 134;
    private static final int BACK_AT_CAB = 164;
    private static final int FANS_ON = 164;
    private static final int STEVE_IN = 168;
    private static final int LIFT_START = 172;
    private static final int GEAR_UP = 182;
    private static final int LIFT_END = 200;
    private static final int CRUISE_END = 240;

    /** How long the gear takes to swing, in ticks. The client tweens the whole thing. */
    private static final int GEAR_TIME = 14;

    /** Ticks between truck teleports. The client tweens the gap, so this is smoothness per byte. */
    private static final int MOVE_STEP = 2;

    /** Ticks between blade poses, and how far each one turns. */
    private static final int BLADE_STEP = 4;
    private static final float BLADE_SWEEP = (float) Math.toRadians(40);

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
    private final Quaternionf turn;

    /** Everything spawned, in spawn order, so teardown is exact rather than by proximity. */
    private final List<Entity> spawned = new ArrayList<Entity>();
    private final List<Entity> truckParts = new ArrayList<Entity>();
    private final List<Entity> carriedBox = new ArrayList<Entity>();

    /** The wheels, paired with the model boxes that describe their two poses. */
    private final List<BlockDisplay> gear = new ArrayList<BlockDisplay>();
    private final List<PartModel.Piece> gearPieces = new ArrayList<PartModel.Piece>();

    /** The fan blades, and where their downwash comes from, already turned to the truck's facing. */
    private final List<BlockDisplay> blades = new ArrayList<BlockDisplay>();
    private final List<PartModel.Piece> bladePieces = new ArrayList<PartModel.Piece>();
    private final List<Vector3f> fanOffsets = new ArrayList<Vector3f>();

    private Villager steve;
    private BukkitTask task;
    private int tick;
    private boolean handedOver;

    /** Where the truck was last put, so sound and downwash come from the truck and not the kerb. */
    private double truckIndex;
    private double altitude;
    private float bladeAngle;

    /**
     * The height it flies in at, hovers at and lifts back to, limited by what is actually open
     * above both the near end of the strip and the pad itself.
     */
    private final int hoverTop;

    /** How high it climbs on the way out, once it is moving forward again. */
    private final int climbTop;

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
        this.turn = new Quaternionf().rotateY(PartRenderer.yawFor(heading));

        // It now stops over the pad and comes straight down, so the pad's own headroom matters as
        // much as the approach end's -- the lower of the two is the only height that is clear for
        // the whole of the inbound leg and the hover.
        this.hoverTop = Math.min(
                road.clearanceAbove(0, MAX_ALTITUDE),
                road.clearanceAbove(road.parkIndex(), MAX_ALTITUDE));
        this.climbTop = Math.max(hoverTop,
                road.clearanceAbove(road.lastIndex(), MAX_ALTITUDE + CLIMB_OUT));

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
     * Sends a truck if there is anywhere for one to land.
     *
     * @return false if there is no strip, in which case the caller should deliver the plain way
     */
    public static boolean dispatch(Player player, List<ComponentType> contents) {
        // A second order while one is on the road goes on the same truck, rather than declining
        // and telling the player there is no room to land when a truck is visibly outside.
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
                + "COMING IN NOW. MIND THE DOWNWASH.");

        PartModel model = PartModels.get(MODEL);
        this.altitude = hoverTop;
        Location start = road.at(0).add(0, altitude, 0);

        List<BlockDisplay> displays = PartRenderer.spawnNamedDisplays(
                start, heading, MODEL, 1.0f, TRUCK_OWNER);

        // Displays come back in model order, so they pair up with the pieces that describe them.
        // That is what lets the wheels and blades be found by what they do rather than by index.
        List<PartModel.Piece> pieces = model.pieces();
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay display = displays.get(i);
            PartModel.Piece piece = i < pieces.size() ? pieces.get(i) : null;

            // Parts are set to a short view range because they are small and there are many. A
            // truck is neither, and one that pops in at eight blocks is worse than no truck.
            display.setViewRange(1.4f);
            display.setTeleportDuration(MOVE_STEP);
            display.setPersistent(false);
            truckParts.add(remember(display));

            if (piece == null) {
                continue;
            }
            if (piece.folds()) {
                gear.add(display);
                gearPieces.add(piece);
                // It arrives flying, so the gear starts stowed. Set instantly: interpolating from
                // the deployed pose would show the wheels folding up as the truck appears.
                display.setTransformation(
                        PartRenderer.transformFor(piece.folded(), turn, 1.0f));
            }
            if (piece.spins()) {
                blades.add(display);
                bladePieces.add(piece);
                fanOffsets.add(new Vector3f(piece.centre()).rotate(turn));
            }
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

        if (tick <= DESCENT_END) {
            approach();
        } else if (tick == DESCENT_END + 1) {
            touchDown();
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
        }

        // Gear and fans sit outside the chain above on purpose. Both fire during phases the chain
        // already claims -- the gear drops mid-approach, the fans spool while Steve is boarding --
        // and an else-if would silently swallow them.
        if (tick == GEAR_DOWN) {
            deployGear(false);
        }

        // Spool up while Steve is still boarding, so the lift-off is not a standing start.
        if (tick >= FANS_ON && tick < LIFT_START) {
            fans((tick - FANS_ON) / (double) (LIFT_START - FANS_ON));
        }
        if (tick == GEAR_UP) {
            deployGear(true);
        }
        if (tick >= LIFT_START && tick <= CRUISE_END) {
            depart();
        }

        // One tick past the end, so the last teleport of the climb-out is actually seen before
        // everything is removed.
        if (tick > CRUISE_END) {
            finish();
        }
    }

    /**
     * Flies in level, stops over the pad, holds, then comes straight down.
     *
     * <p>The inbound leg eases out so it arrives at a standstill rather than sailing past, and the
     * descent is a smoothstep, which is slow off the hover <em>and</em> slow into the ground --
     * the shape a helicopter actually makes, and the reason it reads as settling under power
     * rather than falling.
     */
    private void approach() {
        double index;
        if (tick <= INBOUND_END) {
            double t = tick / (double) INBOUND_END;
            index = road.parkIndex() * (1.0 - (1.0 - t) * (1.0 - t));
            altitude = hoverTop;
        } else if (tick <= HOVER_END) {
            index = road.parkIndex();
            // Not quite still. A dead-static hover looks like the animation has hung.
            altitude = hoverTop + Math.sin(tick * 0.45) * HOVER_BOB;
        } else {
            double t = (tick - HOVER_END) / (double) (DESCENT_END - HOVER_END);
            index = road.parkIndex();
            altitude = hoverTop * (1.0 - smoothstep(t));
        }
        moveTruck(index);
        fans(1.0);
    }

    /** Straight up off the pad first, then forward and climbing. The arrival, in reverse. */
    private void depart() {
        double index;
        if (tick <= LIFT_END) {
            double t = (tick - LIFT_START) / (double) (LIFT_END - LIFT_START);
            index = road.parkIndex();
            altitude = hoverTop * smoothstep(t);
        } else {
            double t = (tick - LIFT_END) / (double) (CRUISE_END - LIFT_END);
            int park = road.parkIndex();
            index = park + (road.lastIndex() - park) * t * t;
            altitude = hoverTop + (climbTop - hoverTop) * t;
        }
        moveTruck(index);
        fans(1.0);
    }

    /** Eases at both ends. Slow off the hover, slow into the ground. */
    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Teleports the whole truck to a point on the lane, at its current height.
     *
     * <p>Only every {@link #MOVE_STEP} ticks: the displays carry a matching teleport duration, so
     * the client interpolates across the gap and the motion is smooth without a packet per tick.
     */
    private void moveTruck(double index) {
        truckIndex = index;
        if (tick % MOVE_STEP != 0) {
            return;
        }
        Location where = truckAt();
        for (Entity part : truckParts) {
            if (part.isValid()) {
                part.teleport(where);
            }
        }
    }

    private Location truckAt() {
        return road.at(truckIndex).add(0, altitude, 0);
    }

    /**
     * Swings the landing gear.
     *
     * <p>The display interpolates from wherever it is now to the pose it is given, so the swing
     * costs one packet per wheel and no further server work.
     *
     * <p>The delay is written last, on the understanding that it is the field which marks the
     * interpolation's start tick. That ordering is the one thing here taken on trust rather than
     * read out of the server -- if the gear snaps into place instead of swinging, this is the
     * line to suspect before anything else.
     *
     * @param stow true to fold flat for flight, false to drop for landing
     */
    private void deployGear(boolean stow) {
        for (int i = 0; i < gear.size(); i++) {
            BlockDisplay wheel = gear.get(i);
            if (!wheel.isValid()) {
                continue;
            }
            PartModel.Piece piece = gearPieces.get(i);
            Transformation pose = PartRenderer.transformFor(
                    stow ? piece.folded() : piece, turn, 1.0f);
            wheel.setTransformation(pose);
            wheel.setInterpolationDuration(GEAR_TIME);
            wheel.setInterpolationDelay(0);
        }
        Location at = truckAt();
        world.playSound(at, stow ? Sound.BLOCK_PISTON_CONTRACT : Sound.BLOCK_PISTON_EXTEND,
                0.8f, 0.7f);
    }

    /**
     * Runs the fans: spins the blades and blows air out from under them.
     *
     * @param intensity 0 to 1; scales how hard the downwash is thrown
     */
    private void fans(double intensity) {
        if (intensity <= 0.0) {
            return;
        }

        if (tick % BLADE_STEP == 0) {
            // Kept climbing rather than wrapped: slerp takes the short way round, so every step
            // reads as continuing rotation. A blade is a bar, not a disc, precisely so this shows.
            bladeAngle += BLADE_SWEEP;
            for (int i = 0; i < blades.size(); i++) {
                BlockDisplay blade = blades.get(i);
                if (!blade.isValid()) {
                    continue;
                }
                blade.setTransformation(spun(bladePieces.get(i), bladeAngle));
                blade.setInterpolationDuration(BLADE_STEP);
                blade.setInterpolationDelay(0);
            }
        }

        if (tick % 2 != 0) {
            return;
        }
        Location at = truckAt();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Vector3f fan : fanOffsets) {
            double x = at.getX() + fan.x;
            double y = at.getY() + fan.y - 0.15;
            double z = at.getZ() + fan.z;
            // Count zero makes the offsets a direction and the last argument a speed, which is the
            // only way to get particles that actually blow downward rather than drift.
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(Particle.CLOUD,
                        x + random.nextDouble(-0.18, 0.18), y,
                        z + random.nextDouble(-0.18, 0.18),
                        0, 0.0, -1.0, 0.0, 0.16 + 0.22 * intensity);
            }
        }

        // Ground effect: close to the pad the downwash has something to hit, and dust coming back
        // up off the ground is most of what makes a hover look like it is holding itself there.
        if (altitude > 0.05 && altitude < 2.6) {
            Location pad = road.at(truckIndex);
            world.spawnParticle(Particle.CLOUD,
                    pad.getX(), pad.getY() + 0.15, pad.getZ(), 3, 1.1, 0.05, 1.1, 0.02);
        }

        if (tick % 6 == 0) {
            world.playSound(at, Sound.ENTITY_MINECART_RIDING, (float) (0.35 * intensity), 1.4f);
            world.playSound(at, Sound.BLOCK_CONDUIT_AMBIENT, (float) (0.25 * intensity), 1.8f);
        }
    }

    /** A blade's pose at some angle: turned about its own centre, then by the truck's facing. */
    private Transformation spun(PartModel.Piece blade, float angle) {
        PartModel.Piece turned = new PartModel.Piece(blade.block(), blade.size(), blade.centre(),
                new Vector3f(0f, 1f, 0f), angle, blade.centre(), null, false);
        return PartRenderer.transformFor(turned, turn, 1.0f);
    }

    /** Settles onto the strip: a thump, and the dust the fans throw up off the ground. */
    private void touchDown() {
        altitude = 0.0;
        Location at = road.at(road.parkIndex());
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_STEP, 0.9f, 0.7f);
        world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 40, 1.3, 0.1, 1.3, 0.04);
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
            // Unaware, not AI-less: an unaware mob keeps its physics -- gravity, step height and
            // the walk animation that comes from actually moving -- while ignoring every goal it
            // has. With AI off entirely he would slide along like a statue; with AI on he would
            // wander off to a bed. Neither is a courier.
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
        Quaternionf carryTurn = new Quaternionf().rotateY(PartRenderer.yawFor(carryFacing));

        for (BlockDisplay display : PartRenderer.spawnNamedDisplays(
                carriedAt(), carryFacing, "package", 1.0f, TRUCK_OWNER)) {
            display.setTeleportDuration(1);
            display.setPersistent(false);
            carriedBox.add(remember(display));
        }
        for (boolean east : new boolean[]{true, false}) {
            TextDisplay sign = Branding.sign(carriedAt(), Branding.COMPANY_SHORT,
                    new Vector3f(east ? 0.26f : -0.26f, 0.24f, 0f), carryTurn, east, 0.5f);
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
        gear.clear();
        blades.clear();
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
