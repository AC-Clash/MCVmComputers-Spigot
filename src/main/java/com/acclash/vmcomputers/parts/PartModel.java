package com.acclash.vmcomputers.parts;

import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

/**
 * A component's shape, as a list of boxes that can each be drawn by one {@code BlockDisplay}.
 *
 * <p>The geometry is converted from the VM Computers mod's Blockbench item models by
 * {@code tools/generate_parts.py} and shipped as {@code parts.json}. A Blockbench element is an
 * axis-aligned box with a size, a centre and an optional rotation about a pivot, which is exactly
 * what a display transformation expresses -- so the shapes come across unchanged.
 *
 * <p>What does not come across is texture. Each box gets one vanilla block chosen for its dominant
 * colour, so parts whose detail is geometry (the case, the graphics card) look close to the mod,
 * while parts that are a single box relying on a painted texture (the keyboard, the hard drive)
 * come out as flat slabs. Detail can be bought back by adding boxes, which is cheap here and was
 * not an option for the mod.
 *
 * <p>Coordinates are in blocks, relative to the part's <em>bottom centre</em>, so a part spawned at
 * a location rests on that spot rather than being buried halfway into it.
 */
public final class PartModel {

    /** One box. Immutable; shared between every copy of a part in the world. */
    public static final class Piece {
        private final BlockData block;
        private final Vector3f size;
        private final Vector3f centre;
        /** Rotation axis as a unit vector, or null when the box is not rotated. */
        private final Vector3f axis;
        /** Rotation angle in radians. */
        private final float angle;
        private final Vector3f pivot;

        Piece(BlockData block, Vector3f size, Vector3f centre, Vector3f axis, float angle,
              Vector3f pivot) {
            this.block = block;
            this.size = size;
            this.centre = centre;
            this.axis = axis;
            this.angle = angle;
            this.pivot = pivot;
        }

        public BlockData block() {
            return block;
        }

        /** Dimensions in blocks. Never zero -- flat elements are given a renderable thickness. */
        public Vector3f size() {
            return new Vector3f(size);
        }

        /** Centre of the box relative to the part's bottom centre, in blocks. */
        public Vector3f centre() {
            return new Vector3f(centre);
        }

        public boolean isRotated() {
            return axis != null;
        }

        public Vector3f axis() {
            return axis == null ? null : new Vector3f(axis);
        }

        public float angle() {
            return angle;
        }

        public Vector3f pivot() {
            return pivot == null ? new Vector3f() : new Vector3f(pivot);
        }
    }

    private final String name;
    private final List<Piece> pieces;

    PartModel(String name, List<Piece> pieces) {
        this.name = name;
        this.pieces = Collections.unmodifiableList(pieces);
    }

    public String name() {
        return name;
    }

    /** How many display entities one copy of this part costs. */
    public int pieceCount() {
        return pieces.size();
    }

    public List<Piece> pieces() {
        return pieces;
    }

    @Override
    public String toString() {
        return "PartModel(" + name + ", " + pieces.size() + " pieces)";
    }
}
