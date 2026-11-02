package com.copycatsplus.copycats.foundation.copycat.model.neoforge;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Combines two assembled copies of a copycat: one with the normal connected textures, and one with partial edges
 * connected, which only shows inside the given regions.
 */
final class PartialEdgeQuads {
    private static final double EPSILON = 1e-5;

    private PartialEdgeQuads() {
    }

    static List<CopycatBakedQuad> merge(List<CopycatBakedQuad> normal, List<CopycatBakedQuad> connected, Map<Direction, List<AABB>> regions) {
        List<CopycatBakedQuad> quads = new ArrayList<>(normal.size() + 4);
        for (CopycatBakedQuad quad : normal) {
            List<AABB> faceRegions = regions.get(quad.getDirection());
            AABB bounds = getBounds(quad);
            if (faceRegions == null || !isFlushRectangle(quad, bounds)) {
                quads.add(quad);
                continue;
            }
            List<AABB> pieces = subtract(List.of(bounds), faceRegions, quad.getDirection().getAxis());
            if (pieces.size() == 1 && pieces.get(0) == bounds) {
                quads.add(quad);
                continue;
            }
            for (AABB piece : pieces) {
                quads.add(crop(quad, piece));
            }
        }
        for (CopycatBakedQuad quad : connected) {
            List<AABB> faceRegions = regions.get(quad.getDirection());
            AABB bounds = getBounds(quad);
            if (faceRegions == null || !isFlushRectangle(quad, bounds))
                continue;
            Axis normalAxis = quad.getDirection().getAxis();
            for (int i = 0; i < faceRegions.size(); i++) {
                AABB region = faceRegions.get(i);
                if (!overlaps(bounds, region, normalAxis))
                    continue;
                // Regions can overlap at a corner of the face
                for (AABB piece : subtract(List.of(intersect(bounds, region, normalAxis)), faceRegions.subList(0, i), normalAxis)) {
                    quads.add(crop(quad, piece));
                }
            }
        }
        return quads;
    }

    private static List<AABB> subtract(List<AABB> boxes, List<AABB> regions, Axis normalAxis) {
        List<AABB> remaining = boxes;
        for (AABB region : regions) {
            List<AABB> next = new ArrayList<>();
            for (AABB box : remaining) {
                if (!overlaps(box, region, normalAxis)) {
                    next.add(box);
                    continue;
                }
                Axis first = normalAxis == Axis.X ? Axis.Y : Axis.X;
                Axis second = normalAxis == Axis.Z ? Axis.Y : Axis.Z;
                double firstMin = Math.max(box.min(first), region.min(first));
                double firstMax = Math.min(box.max(first), region.max(first));
                addIfNotEmpty(next, withRange(box, first, box.min(first), firstMin), normalAxis);
                addIfNotEmpty(next, withRange(box, first, firstMax, box.max(first)), normalAxis);
                AABB middle = withRange(box, first, firstMin, firstMax);
                addIfNotEmpty(next, withRange(middle, second, box.min(second), region.min(second)), normalAxis);
                addIfNotEmpty(next, withRange(middle, second, region.max(second), box.max(second)), normalAxis);
            }
            remaining = next;
        }
        return remaining;
    }

    private static void addIfNotEmpty(List<AABB> boxes, AABB box, Axis normalAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != normalAxis && box.max(axis) - box.min(axis) < EPSILON) return;
        }
        boxes.add(box);
    }

    private static AABB withRange(AABB box, Axis axis, double min, double max) {
        return new AABB(
                axis == Axis.X ? min : box.minX, axis == Axis.Y ? min : box.minY, axis == Axis.Z ? min : box.minZ,
                axis == Axis.X ? max : box.maxX, axis == Axis.Y ? max : box.maxY, axis == Axis.Z ? max : box.maxZ
        );
    }

    private static AABB intersect(AABB box, AABB region, Axis normalAxis) {
        AABB result = box;
        for (Axis axis : Axis.values()) {
            if (axis != normalAxis)
                result = withRange(result, axis, Math.max(box.min(axis), region.min(axis)), Math.min(box.max(axis), region.max(axis)));
        }
        return result;
    }

    private static boolean overlaps(AABB box, AABB region, Axis normalAxis) {
        for (Axis axis : Axis.values()) {
            if (axis != normalAxis && (box.max(axis) <= region.min(axis) + EPSILON || box.min(axis) >= region.max(axis) - EPSILON))
                return false;
        }
        return true;
    }

    /**
     * Whether a quad lies flat on the outermost pixel of its face and can be cropped. This leaves out sloped faces,
     * inner steps, and the slanted side walls of slopes, which crop into the wrong shape.
     */
    private static boolean isFlushRectangle(CopycatBakedQuad quad, AABB bounds) {
        Direction face = quad.getDirection();
        Axis axis = face.getAxis();
        if (bounds.max(axis) - bounds.min(axis) > EPSILON) return false;
        double depth = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - bounds.max(axis) : bounds.min(axis);
        return depth < 1 / 16.0 + EPSILON && hasFourCorners(quad, bounds, axis);
    }

    private static boolean hasFourCorners(CopycatBakedQuad quad, AABB bounds, Axis normalAxis) {
        Axis first = normalAxis == Axis.X ? Axis.Y : Axis.X;
        Axis second = normalAxis == Axis.Z ? Axis.Y : Axis.Z;
        int corners = 0;
        for (int i = 0; i < 4; i++) {
            Vec3 xyz = BakedQuadHelper.getXYZ(quad.getVertices(), i);
            double a = xyz.get(first), b = xyz.get(second);
            boolean atFirstMin = Math.abs(a - bounds.min(first)) < EPSILON;
            boolean atSecondMin = Math.abs(b - bounds.min(second)) < EPSILON;
            if (!atFirstMin && Math.abs(a - bounds.max(first)) >= EPSILON) return false;
            if (!atSecondMin && Math.abs(b - bounds.max(second)) >= EPSILON) return false;
            corners |= 1 << ((atFirstMin ? 0 : 1) + (atSecondMin ? 0 : 2));
        }
        return corners == 0b1111;
    }

    private static AABB getBounds(CopycatBakedQuad quad) {
        int[] vertices = quad.getVertices();
        Vec3 first = BakedQuadHelper.getXYZ(vertices, 0);
        double minX = first.x, minY = first.y, minZ = first.z, maxX = first.x, maxY = first.y, maxZ = first.z;
        for (int i = 1; i < 4; i++) {
            Vec3 xyz = BakedQuadHelper.getXYZ(vertices, i);
            minX = Math.min(minX, xyz.x);
            minY = Math.min(minY, xyz.y);
            minZ = Math.min(minZ, xyz.z);
            maxX = Math.max(maxX, xyz.x);
            maxY = Math.max(maxY, xyz.y);
            maxZ = Math.max(maxZ, xyz.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static CopycatBakedQuad crop(CopycatBakedQuad quad, AABB box) {
        int[] vertices = BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), box, Vec3.ZERO);
        return new CopycatBakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.cullFace, quad.property);
    }
}
