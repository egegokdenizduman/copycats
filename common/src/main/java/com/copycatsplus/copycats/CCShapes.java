package com.copycatsplus.copycats;

import com.copycatsplus.copycats.CCBlockStateProperties.Side;
import com.copycatsplus.copycats.CCBlockStateProperties.VerticalStairShape;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.copycatsplus.copycats.content.copycat.half_panel.CopycatHalfPanelBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.vertical_stairs.CopycatVerticalStairBlock;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableAABB;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVec3;
import com.copycatsplus.copycats.utility.TriFunction;
import com.copycatsplus.copycats.utility.shape.OutlinedVoxelShape;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.*;

public class CCShapes {

    public static final Map<Direction, MutableShape> BOARD =
            forDirections(shape(aabb(16, 16, 1).move(0, 0, 15)));
    public static final Map<Axis, MutableShape> SLAB_BOTTOM =
            forAxes(shape(aabb(16, 16, 8)));
    public static final Map<Axis, MutableShape> SLAB_TOP =
            forAxes(shape(aabb(16, 16, 8).move(0, 0, 8)));
    public static final Map<Axis, MutableShape> BEAM =
            forAxes(shape(aabb(8, 8, 16).move(4, 4, 0)));
    public static final Map<Direction, MutableShape> VERTICAL_STEP =
            forHorizontalDirections(shape(aabb(8, 16, 8).move(8, 0, 8)));
    public static final Map<Direction, Map<Integer, MutableShape>> LAYER =
            forDirections(forAll(LAYERS,
                    layer -> shape(
                            aabb(16, 16, layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Direction, MutableShape>> HALF_PANEL =
            forAll(CopycatHalfPanelBlock.FACING, CopycatHalfPanelBlock.OFFSET,
                    (direction, offset) -> {
                        MutableShape shape = shape(
                                aabb(16, 8, 3).move(0, 8, 13)
                        ).rotateZ((int) -offset.toYRot());
                        directions(direction).apply(shape);
                        // todo: clean up this cryptic mess
                        if (direction.getAxis() == Axis.X) {
                            shape.rotateX(-90).flipY(direction == Direction.WEST);
                        }
                        if (direction == Direction.NORTH) {
                            shape.flipX(true);
                        }
                        if (direction == Direction.UP) {
                            shape.flipZ(true);
                        }
                        return shape;
                    }
            );
    public static final Map<Direction, Map<Half, Map<Integer, MutableShape>>> SLICE =
            forHorizontalDirections(forHalves(forAll(LAYERS,
                    layer -> shape(
                            aabb(16, layer * 2, layer * 2).move(0, 0, 16 - layer * 2)
                    )
            )));
    public static final Map<Direction, Map<Integer, MutableShape>> VERTICAL_SLICE =
            forHorizontalDirections(forAll(LAYERS,
                    layer -> shape(
                            aabb(layer * 2, 16, layer * 2).move(16 - layer * 2, 0, 16 - layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Half, Map<Integer, MutableShape>>> CORNER_SLICE =
            forHorizontalDirections(forHalves(forAll(LAYERS,
                    layer -> shape(
                            aabb(layer * 2, layer * 2, layer * 2).move(16 - layer * 2, 0, 16 - layer * 2)
                    )
            )));
    public static final Map<Axis, Map<Half, Map<Integer, MutableShape>>> HALF_LAYER_BOTTOM =
            forHorizontalAxes(forHalves(forAll(CopycatHalfLayerBlock.NEGATIVE_LAYERS,
                    layer -> shape(
                            aabb(16, layer * 2, 8)
                    )
            )));
    public static final Map<Axis, Map<Half, Map<Integer, MutableShape>>> HALF_LAYER_TOP =
            forHorizontalAxes(forHalves(forAll(CopycatHalfLayerBlock.POSITIVE_LAYERS,
                    layer -> shape(
                            aabb(16, layer * 2, 8).move(0, 0, 8)
                    )
            )));
    public static final Map<Axis, MutableShape> HORIZONTAL_PANE = forAxes(shape(
            aabb(16, 16, 2).move(0, 0, 7)
    ));
    public static final Map<Direction, Map<Integer, MutableShape>> VERTICAL_HALF_LAYER_LEFT =
            forHorizontalDirections(forAll(CopycatHalfLayerBlock.POSITIVE_LAYERS,
                    layer -> shape(
                            aabb(8, 16, layer * 2).move(8, 0, 16 - layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Integer, MutableShape>> VERTICAL_HALF_LAYER_RIGHT =
            forHorizontalDirections(forAll(CopycatHalfLayerBlock.NEGATIVE_LAYERS,
                    layer -> shape(
                            aabb(8, 16, layer * 2).move(0, 0, 16 - layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Integer, MutableShape>> STACKED_HALF_LAYER_TOP =
            forHorizontalDirections(forAll(CopycatHalfLayerBlock.POSITIVE_LAYERS,
                    layer -> shape(
                            aabb(16, 8, layer * 2).move(0, 8, 16 - layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Integer, MutableShape>> STACKED_HALF_LAYER_BOTTOM =
            forHorizontalDirections(forAll(CopycatHalfLayerBlock.NEGATIVE_LAYERS,
                    layer -> shape(
                            aabb(16, 8, layer * 2).move(0, 0, 16 - layer * 2)
                    )
            ));
    public static final Map<Direction, Map<Side, Map<VerticalStairShape, MutableShape>>> VERTICAL_STAIR =
            forHorizontalDirections(forAll(CopycatVerticalStairBlock.SIDE, CopycatVerticalStairBlock.SHAPE,
                    (side, shape) ->
                            (switch (shape) {
                                case STRAIGHT -> shape(
                                        aabb(16, 16, 8).move(0, 0, 8),
                                        aabb(8, 16, 8).move(8, 0, 0)
                                );
                                case OUTER_TOP, OUTER_BOTTOM -> shape(
                                        aabb(16, 16, 8).move(0, 0, 8),
                                        aabb(8, 8, 8).move(8, 0, 0)
                                ).flipY(shape == VerticalStairShape.OUTER_TOP);
                                case INNER_TOP, INNER_BOTTOM -> shape(
                                        aabb(16, 16, 8).move(0, 0, 8),
                                        aabb(8, 16, 8).move(8, 0, 0),
                                        aabb(8, 8, 8)
                                ).flipY(shape == VerticalStairShape.INNER_TOP);
                            })
                                    .flipX(side == Side.RIGHT)
            ));
    private static final int SLOPE_SUBDIVISIONS = 16;
    private static final double SLOPE_STEP = 16.0 / SLOPE_SUBDIVISIONS;
    public static final Map<Direction, Map<Half, Map<StairsShape, MutableShape>>> SLOPE =
            forHorizontalDirections(forHalves(forAll(STAIRS_SHAPE,
                    slopeShape -> switch (slopeShape) {
                        case STRAIGHT -> shape(
                                IntStream.range(0, SLOPE_SUBDIVISIONS)
                                        .mapToObj(i -> aabb(16, (i + 1.0) / SLOPE_SUBDIVISIONS * 16.0, 16.0 / SLOPE_SUBDIVISIONS).move(0, 0, i * 16.0 / SLOPE_SUBDIVISIONS))
                                        .toArray(MutableAABB[]::new)
                        ).outline(
                                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                                line(vec3(0, 0, 16), vec3(0, 16, 16)),
                                line(vec3(16, 0, 16), vec3(16, 16, 16)),
                                line(vec3(0, 16, 16), vec3(16, 16, 16)),
                                line(vec3(0, 0, 0), vec3(0, 16, 16)),
                                line(vec3(16, 0, 0), vec3(16, 16, 16))
                        );
                        // two straight slopes joined, rising to the south and east
                        case INNER_LEFT, INNER_RIGHT -> shape(
                                IntStream.range(0, SLOPE_SUBDIVISIONS).boxed()
                                        .flatMap(i -> Stream.of(
                                                aabb(16, (i + 1) * SLOPE_STEP, 16 - i * SLOPE_STEP).move(0, 0, i * SLOPE_STEP),
                                                aabb(16 - i * SLOPE_STEP, (i + 1) * SLOPE_STEP, 16).move(i * SLOPE_STEP, 0, 0)
                                        ))
                                        .toArray(MutableAABB[]::new)
                        ).outline(
                                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                                line(vec3(16, 0, 0), vec3(16, 16, 0)),
                                line(vec3(0, 0, 16), vec3(0, 16, 16)),
                                line(vec3(16, 0, 16), vec3(16, 16, 16)),
                                line(vec3(16, 16, 0), vec3(16, 16, 16)),
                                line(vec3(0, 16, 16), vec3(16, 16, 16)),
                                line(vec3(0, 0, 0), vec3(16, 16, 0)),
                                line(vec3(0, 0, 0), vec3(0, 16, 16)),
                                line(vec3(0, 0, 0), vec3(16, 16, 16))
                        ).flipX(slopeShape == StairsShape.INNER_RIGHT);
                        // the overlap of those two slopes, peaking at the south-east corner
                        case OUTER_LEFT, OUTER_RIGHT -> shape(
                                IntStream.range(0, SLOPE_SUBDIVISIONS)
                                        .mapToObj(i -> aabb(16 - i * SLOPE_STEP, (i + 1) * SLOPE_STEP, 16 - i * SLOPE_STEP).move(i * SLOPE_STEP, 0, i * SLOPE_STEP))
                                        .toArray(MutableAABB[]::new)
                        ).outline(
                                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                                line(vec3(16, 0, 16), vec3(16, 16, 16)),
                                line(vec3(0, 0, 16), vec3(16, 16, 16)),
                                line(vec3(16, 0, 0), vec3(16, 16, 16)),
                                line(vec3(0, 0, 0), vec3(16, 16, 16))
                        ).flipX(slopeShape == StairsShape.OUTER_RIGHT);
                    }
            )));
    public static final Map<Direction, MutableShape> VERTICAL_SLOPE =
            forHorizontalDirections(shape(
                    IntStream.range(0, SLOPE_SUBDIVISIONS)
                            .mapToObj(i -> aabb((i + 1.0) / SLOPE_SUBDIVISIONS * 16.0, 16, 16.0 / SLOPE_SUBDIVISIONS).move(15 - i * 16.0 / SLOPE_SUBDIVISIONS, 0, i * 16.0 / SLOPE_SUBDIVISIONS))
                            .toArray(MutableAABB[]::new)
            ).outline(
                    line(vec3(0, 0, 16), vec3(16, 0, 16)),
                    line(vec3(0, 0, 16), vec3(0, 16, 16)),
                    line(vec3(16, 0, 16), vec3(16, 16, 16)),
                    line(vec3(0, 16, 16), vec3(16, 16, 16)),
                    line(vec3(16, 0, 0), vec3(16, 0, 16)),
                    line(vec3(16, 16, 0), vec3(16, 16, 16)),
                    line(vec3(16, 0, 0), vec3(16, 16, 0)),
                    line(vec3(16, 0, 0), vec3(0, 0, 16)),
                    line(vec3(16, 16, 0), vec3(0, 16, 16))
            ));
    public static final Map<Direction, Map<Half, Map<Boolean, Map<Integer, MutableShape>>>> SLOPE_LAYER =
            forHorizontalDirections(forHalves(forAll(IN_WALL,
                    in_wall -> forAll(CopycatSlopeLayerBlock.LAYERS,
                            layer -> {
                                int low = CopycatSlopeLayerBlock.getLowEdge(layer);
                                int high = CopycatSlopeLayerBlock.getHighEdge(layer);
                                MutableShape baseShape = CopycatSlopeLayerBlock.getKink(layer) != 0 ? kinkedSlopeLayer(layer) : layer <= 4 ?
                                        shape(
                                                IntStream.range(0, SLOPE_SUBDIVISIONS)
                                                        .mapToObj(i -> aabb(16, (i + 1.0) / SLOPE_SUBDIVISIONS * 16 * layer / 4.0, 16.0 / SLOPE_SUBDIVISIONS).move(0, 0, i * 16.0 / SLOPE_SUBDIVISIONS))
                                                        .toArray(MutableAABB[]::new)
                                        ).outline(
                                                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                                                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                                                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                                                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                                                line(vec3(0, 0, 16), vec3(0, 4 * layer, 16)),
                                                line(vec3(16, 0, 16), vec3(16, 4 * layer, 16)),
                                                line(vec3(0, 4 * layer, 16), vec3(16, 4 * layer, 16)),
                                                line(vec3(0, 0, 0), vec3(0, 4 * layer, 16)),
                                                line(vec3(16, 0, 0), vec3(16, 4 * layer, 16))
                                        ) :
                                        shape(
                                                IntStream.range(0, SLOPE_SUBDIVISIONS)
                                                        .mapToObj(i -> aabb(16, 4 * low + (i + 1.0) / SLOPE_SUBDIVISIONS * 4 * (high - low), 16.0 / SLOPE_SUBDIVISIONS).move(0, 0, i * 16.0 / SLOPE_SUBDIVISIONS))
                                                        .toArray(MutableAABB[]::new)
                                        ).outline(
                                                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                                                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                                                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                                                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                                                line(vec3(0, 0, 0), vec3(0, low * 4, 0)),
                                                line(vec3(16, 0, 0), vec3(16, low * 4, 0)),
                                                line(vec3(0, 0, 16), vec3(0, high * 4, 16)),
                                                line(vec3(16, 0, 16), vec3(16, high * 4, 16)),
                                                line(vec3(0, high * 4, 16), vec3(16, high * 4, 16)),
                                                line(vec3(0, low * 4, 0), vec3(16, low * 4, 0)),
                                                line(vec3(0, low * 4, 0), vec3(0, high * 4, 16)),
                                                line(vec3(16, low * 4, 0), vec3(16, high * 4, 16))
                                        );

                                MutableShape shape = baseShape.copy();
                                if (in_wall) {
                                    shape.rotateX(270);
                                }

                                return shape;
                            }
                    ))));
    public static final Map<Direction, Map<Half, Map<Integer, MutableShape>>> VERTICAL_SLOPE_LAYER =
            forHorizontalDirections(forAll(HALF, half -> forAll(CopycatSlopeLayerBlock.LAYERS,
                    layer -> SLOPE_LAYER.get(Direction.SOUTH).get(Half.BOTTOM).get(false).get(layer).copy()
                            .rotateZ(-90).flipX(half == Half.TOP)
            )));
    public static final Map<Direction, Map<Half, Map<StairsShape, Map<Integer, MutableShape>>>> SLOPE_LAYER_CORNER =
            forHorizontalDirections(forHalves(forAll(STAIRS_SHAPE, CopycatSlopeLayerBlock.LAYERS,
                    (slopeShape, layer) -> {
                        double low = 4 * CopycatSlopeLayerBlock.getLowEdge(layer);
                        double high = 4 * CopycatSlopeLayerBlock.getHighEdge(layer);
                        return switch (slopeShape) {
                            // only corners are looked up here
                            case STRAIGHT -> shape();
                            case INNER_LEFT, INNER_RIGHT -> innerSlopeCorner(low, high).flipX(slopeShape == StairsShape.INNER_RIGHT);
                            case OUTER_LEFT, OUTER_RIGHT -> outerSlopeCorner(low, high).flipX(slopeShape == StairsShape.OUTER_RIGHT);
                        };
                    }
            )));

    // a steep run piece, flat on one side of the kink
    private static MutableShape kinkedSlopeLayer(int layer) {
        double low = 4 * CopycatSlopeLayerBlock.getLowEdge(layer);
        double high = 4 * CopycatSlopeLayerBlock.getHighEdge(layer);
        double kink = CopycatSlopeLayerBlock.getKink(layer);
        double kinkHeight = low > 0 ? high : 0;
        MutableShape shape = shape(
                IntStream.range(0, SLOPE_SUBDIVISIONS)
                        .mapToObj(i -> aabb(16, CopycatSlopeLayerBlock.getSurfaceHeight(layer, (i + 1) * SLOPE_STEP), SLOPE_STEP).move(0, 0, i * SLOPE_STEP))
                        .toArray(MutableAABB[]::new)
        ).outline(
                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                line(vec3(0, 0, 16), vec3(0, high, 16)),
                line(vec3(16, 0, 16), vec3(16, high, 16)),
                line(vec3(0, high, 16), vec3(16, high, 16)),
                line(vec3(0, kinkHeight, kink), vec3(16, kinkHeight, kink)),
                line(vec3(0, kinkHeight, kink), vec3(0, high, 16)),
                line(vec3(16, kinkHeight, kink), vec3(16, high, 16))
        );
        return low > 0 ? shape.outline(
                line(vec3(0, 0, 0), vec3(0, low, 0)),
                line(vec3(16, 0, 0), vec3(16, low, 0)),
                line(vec3(0, low, 0), vec3(16, low, 0)),
                line(vec3(0, low, 0), vec3(0, high, kink)),
                line(vec3(16, low, 0), vec3(16, high, kink))
        ) : shape;
    }

    // two straight slope layers joined, rising to the south and east
    private static MutableShape innerSlopeCorner(double low, double high) {
        double step = (high - low) / SLOPE_SUBDIVISIONS;
        MutableShape corner = shape(
                IntStream.range(0, SLOPE_SUBDIVISIONS).boxed()
                        .flatMap(i -> Stream.of(
                                aabb(16, low + (i + 1) * step, 16 - i * SLOPE_STEP).move(0, 0, i * SLOPE_STEP),
                                aabb(16 - i * SLOPE_STEP, low + (i + 1) * step, 16).move(i * SLOPE_STEP, 0, 0)
                        ))
                        .toArray(MutableAABB[]::new)
        ).outline(
                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                line(vec3(16, 0, 0), vec3(16, high, 0)),
                line(vec3(0, 0, 16), vec3(0, high, 16)),
                line(vec3(16, 0, 16), vec3(16, high, 16)),
                line(vec3(16, high, 0), vec3(16, high, 16)),
                line(vec3(0, high, 16), vec3(16, high, 16)),
                line(vec3(0, low, 0), vec3(16, high, 0)),
                line(vec3(0, low, 0), vec3(0, high, 16)),
                line(vec3(0, low, 0), vec3(16, high, 16))
        );
        return low > 0 ? corner.outline(line(vec3(0, 0, 0), vec3(0, low, 0))) : corner;
    }

    // the overlap of those two slope layers, peaking at the south-east corner
    private static MutableShape outerSlopeCorner(double low, double high) {
        double step = (high - low) / SLOPE_SUBDIVISIONS;
        MutableShape corner = shape(
                IntStream.range(0, SLOPE_SUBDIVISIONS)
                        .mapToObj(i -> aabb(16 - i * SLOPE_STEP, low + (i + 1) * step, 16 - i * SLOPE_STEP).move(i * SLOPE_STEP, 0, i * SLOPE_STEP))
                        .toArray(MutableAABB[]::new)
        ).outline(
                line(vec3(0, 0, 0), vec3(16, 0, 0)),
                line(vec3(0, 0, 16), vec3(16, 0, 16)),
                line(vec3(0, 0, 0), vec3(0, 0, 16)),
                line(vec3(16, 0, 0), vec3(16, 0, 16)),
                line(vec3(16, 0, 16), vec3(16, high, 16)),
                line(vec3(0, low, 16), vec3(16, high, 16)),
                line(vec3(16, low, 0), vec3(16, high, 16)),
                line(vec3(0, low, 0), vec3(16, high, 16))
        );
        return low > 0 ? corner.outline(
                line(vec3(0, 0, 0), vec3(0, low, 0)),
                line(vec3(16, 0, 0), vec3(16, low, 0)),
                line(vec3(0, 0, 16), vec3(0, low, 16)),
                line(vec3(0, low, 0), vec3(16, low, 0)),
                line(vec3(0, low, 0), vec3(0, low, 16))
        ) : corner;
    }

    public static AssemblyTransform halves(Half half) {
        return t -> t.flipY(half == Half.TOP);
    }

    public static AssemblyTransform axes(Axis axis) {
        if (axis == Axis.Z)
            return AssemblyTransform.IDENTITY;
        else if (axis == Axis.Y)
            return t -> t.rotateX(90);
        else
            return t -> t.rotateY(-90);
    }

    public static AssemblyTransform directions(Direction direction) {
        if (direction.getAxis().isHorizontal())
            return t -> t.rotateY((int) direction.toYRot());
        else if (direction == Direction.UP)
            return t -> t.rotateX(90);
        else if (direction == Direction.DOWN)
            return t -> t.rotateX(-90);
        else
            return AssemblyTransform.IDENTITY;
    }

    private static <T, U> Map<T, U> applyTransform(Map<T, U> source, AssemblyTransform transform) {
        Map<T, U> copy = new HashMap<>();
        for (Map.Entry<T, U> entry : source.entrySet()) {
            copy.put(entry.getKey(), applyTransform(entry.getValue(), transform));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static <U> U applyTransform(U source, AssemblyTransform transform) {
        if (source instanceof MutableShape shape) {
            MutableShape copy = shape.copy();
            transform.apply(copy);
            return (U) copy;
        } else if (source instanceof Map<?, ?> map) {
            return (U) applyTransform(map, transform);
        } else {
            throw new RuntimeException("Unsupported type: " + source.getClass().getName());
        }
    }

    /**
     * Create a map for all possible values of {@link Axis}. The input shape should be modeled for the Z axis.
     */
    public static <U> Map<Axis, U> forAxes(U factory) {
        return forAll(AXIS, axis -> applyTransform(factory, axes(axis)));
    }

    /**
     * Create a map for all possible horizontal values of {@link Axis}. The input shape should be modeled for the Z axis.
     */
    public static <U> Map<Axis, U> forHorizontalAxes(U factory) {
        return forAll(HORIZONTAL_AXIS, axis -> applyTransform(factory, axes(axis)));
    }

    /**
     * Create a map for all possible values of {@link Direction}. The input shape should be modeled for the SOUTH direction
     */
    public static <U> Map<Direction, U> forDirections(U factory) {
        return forAll(FACING, direction -> applyTransform(factory, directions(direction)));
    }

    /**
     * Create a map for all possible horizontal values of {@link Direction}. The input shape should be modeled for the SOUTH direction
     */
    public static <U> Map<Direction, U> forHorizontalDirections(U factory) {
        return forAll(HORIZONTAL_FACING, direction -> applyTransform(factory, directions(direction)));
    }

    /**
     * Create a map for all possible values of {@link Half}. The input shape should be modeled for the BOTTOM half.
     */
    public static <U> Map<Half, U> forHalves(U factory) {
        return forAll(HALF, halves -> applyTransform(factory, halves(halves)));
    }

    /**
     * Create a map for all possible values of a property.
     */
    public static <T extends Comparable<T>, U> Map<T, U> forAll(Property<T> property, Function<T, U> factory) {
        return property.getPossibleValues().stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        factory
                )
        );
    }

    /**
     * Create a map of maps for all possible combinations of two properties.
     */
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, U> Map<T1, Map<T2, U>> forAll(Property<T1> property1,
                                                                                                       Property<T2> property2,
                                                                                                       BiFunction<T1, T2, U> factory) {
        return property1.getPossibleValues().stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        t1 -> forAll(property2, t2 -> factory.apply(t1, t2))
                )
        );
    }

    /**
     * Create a map of maps for all possible combinations of three properties.
     */
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, U> Map<T1, Map<T2, Map<T3, U>>> forAll(Property<T1> property1,
                                                                                                                                           Property<T2> property2,
                                                                                                                                           Property<T3> property3,
                                                                                                                                           TriFunction<T1, T2, T3, U> factory) {
        return property1.getPossibleValues().stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        t1 -> forAll(property2, property3, (t2, t3) -> factory.apply(t1, t2, t3))
                )
        );
    }

    /**
     * Create a mutable shape consisting of multiple {@link MutableAABB}s.
     */
    public static MutableShape shape(MutableAABB... boxes) {
        List<MutableAABB> newBoxes = new LinkedList<>();
        Collections.addAll(newBoxes, boxes);
        return new MutableShape(newBoxes, new ArrayList<>());
    }

    /**
     * Create a mutable line consisting of two {@link MutableVec3}s.
     */
    public static Pair<MutableVec3, MutableVec3> line(MutableVec3 start, MutableVec3 end) {
        return Pair.of(start, end);
    }

    public static class MutableShape implements AssemblyTransform.Transformable<MutableShape> {
        public List<MutableAABB> boxes;
        public List<Pair<MutableVec3, MutableVec3>> outlines;

        private VoxelShape output = null;

        public MutableShape(List<MutableAABB> boxes, List<Pair<MutableVec3, MutableVec3>> outlines) {
            this.boxes = boxes;
            this.outlines = outlines;
        }

        public MutableShape() {
            this.boxes = new LinkedList<>();
            this.outlines = new LinkedList<>();
        }

        @SafeVarargs
        public final MutableShape outline(Pair<MutableVec3, MutableVec3>... lines) {
            Collections.addAll(outlines, lines);
            return this;
        }

        public MutableShape copy() {
            return new MutableShape(
                    boxes.stream().map(MutableAABB::copy).collect(Collectors.toList()),
                    outlines.stream().map(p -> Pair.of(p.getFirst().copy(), p.getSecond().copy())).collect(Collectors.toList())
            );
        }

        public VoxelShape toShape() {
            if (output == null) {
                VoxelShape shape = Shapes.empty();
                for (MutableAABB box : boxes) {
                    shape = Shapes.joinUnoptimized(shape, Shapes.box(
                            Math.min(box.minX, box.maxX),
                            Math.min(box.minY, box.maxY),
                            Math.min(box.minZ, box.maxZ),
                            Math.max(box.maxX, box.minX),
                            Math.max(box.maxY, box.minY),
                            Math.max(box.maxZ, box.minZ)
                    ), BooleanOp.OR);
                }
                shape = shape.optimize();
                if (!outlines.isEmpty()) {
                    shape = new OutlinedVoxelShape(shape, outlines.stream().map(p -> Pair.of(p.getFirst().toVec3(), p.getSecond().toVec3())).collect(Collectors.toList()));
                }
                output = shape;
            }
            return output;
        }

        @Override
        public MutableShape rotateX(int angle) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.rotateX(angle);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().rotateX(angle);
                outline.getSecond().rotateX(angle);
            }
            return this;
        }

        @Override
        public MutableShape rotateY(int angle) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.rotateY(angle);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().rotateY(angle);
                outline.getSecond().rotateY(angle);
            }
            return this;
        }

        @Override
        public MutableShape rotateZ(int angle) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.rotateZ(angle);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().rotateZ(angle);
                outline.getSecond().rotateZ(angle);
            }
            return this;
        }

        @Override
        public MutableShape flipX(boolean flip) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.flipX(flip);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().flipX(flip);
                outline.getSecond().flipX(flip);
            }
            return this;
        }

        @Override
        public MutableShape flipY(boolean flip) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.flipY(flip);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().flipY(flip);
                outline.getSecond().flipY(flip);
            }
            return this;
        }

        @Override
        public MutableShape flipZ(boolean flip) {
            if (output != null) throw new IllegalStateException("Cannot modify a shape after it has been built");
            for (MutableAABB box : boxes) {
                box.flipZ(flip);
            }
            for (Pair<MutableVec3, MutableVec3> outline : outlines) {
                outline.getFirst().flipZ(flip);
                outline.getSecond().flipZ(flip);
            }
            return this;
        }
    }
}
