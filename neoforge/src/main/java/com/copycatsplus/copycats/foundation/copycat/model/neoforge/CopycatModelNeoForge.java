package com.copycatsplus.copycats.foundation.copycat.model.neoforge;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.CopycatExternalContext;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.FilteredBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.ScaledBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.copycatsplus.copycats.utility.BlockFaceUtils;
import com.copycatsplus.copycats.utility.neoforge.ModelDataUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.content.decoration.copycat.CopycatSpecialCases;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.createmod.catnip.data.Iterate;
import net.createmod.ponder.render.VirtualRenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.copycatsplus.copycats.CCBlockStateProperties.BASE_TYPE;
import static com.copycatsplus.copycats.foundation.copycat.CopycatBaseBlock.BASE_TYPE_COUNT;
import static com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore.MATERIAL_KEY;
import static com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore.getModelOf;

public class CopycatModelNeoForge extends BakedModelWrapperWithData {

    public static final ModelProperty<BlockState> MATERIAL_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<Map<String, BlockState>> MATERIALS_PROPERTY = new ModelProperty<>();
    private static final ModelProperty<Map<String, OcclusionData>> OCCLUSION_PROPERTY = new ModelProperty<>();
    private static final ModelProperty<Map<String, ModelData>> WRAPPED_DATA_PROPERTY = new ModelProperty<>();
    private static final ModelProperty<Set<String>> EMISSIVE_PROPERTY = new ModelProperty<>();
    private static final ModelProperty<Integer> VARIANT_PROPERTY = new ModelProperty<>();
    private static final ModelProperty<PartialEdgeData> PARTIAL_EDGE_PROPERTY = new ModelProperty<>();

    private static final ChunkRenderTypeSet allRenderTypes = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout(), RenderType.cutoutMipped(), RenderType.translucent());

    protected final CopycatModelCore core;
    private final boolean disableAO;
    protected final List<CopycatModelCore.ModelEntry> entries = new ArrayList<>();
    private final ThreadLocal<RenderSession> renderSession = ThreadLocal.withInitial(() -> new RenderSession(this::getQuads));

    public CopycatModelNeoForge(BakedModel originalModel, CopycatModelCore core, boolean disableAO) {
        super(originalModel);
        this.core = core;
        this.disableAO = disableAO;
        core.registerModels(entries);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public boolean useAmbientOcclusion() {
        if (disableAO) return false;
        return super.useAmbientOcclusion();
    }

    @Override
    public @NotNull TriState useAmbientOcclusion(@NotNull BlockState state, @NotNull ModelData data, @NotNull RenderType renderType) {
        if (disableAO) return TriState.FALSE;
        return super.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        Map<String, BlockState> materials = getMaterials(data);
        // getQuads draws the base without a material, and model data may not be there yet
        if (materials.isEmpty())
            return allRenderTypes;
        ChunkRenderTypeSet renderTypes = ChunkRenderTypeSet.none();
        Map<String, ModelData> wrappedDataMap = getWrappedData(data);
        prepareModelCore(state, rand, data);
        for (CopycatModelCore.ModelEntry entry : entries) {
            BlockState material = entry.materialMapper().map(state, materials.get(entry.key()));
            if (material == null && entry.type().useCopycatLogic())
                continue;
            BakedModel model = getModelForEntry(entry, state, material);
            if (model == null)
                continue;
            if (entry.type().useCopycatLogic()) {
                ModelData wrappedData = wrappedDataMap.getOrDefault(entry.key(), ModelData.EMPTY);
                renderTypes = ChunkRenderTypeSet.union(renderTypes, model.getRenderTypes(material, rand, wrappedData));
            } else {
                renderTypes = ChunkRenderTypeSet.union(renderTypes, model.getRenderTypes(state, rand, data));
            }
        }
        return renderTypes;
    }

    @Override
    public ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
                                             ModelData blockEntityData) {
        if (!(originalModel instanceof BakedModelWrapperWithData)) {
            ModelDataUtils.copyModelData(originalModel.getModelData(world, pos, state, blockEntityData), builder);
        }

        Map<String, BlockState> materials = getMaterials(blockEntityData);
        if (materials.isEmpty()) {
            BlockState material = blockEntityData.get(MATERIAL_PROPERTY);
            if (material != null)
                materials = Map.of(MATERIAL_KEY, material);
        }
        if (materials.isEmpty())
            return builder;

        builder.with(MATERIALS_PROPERTY, new HashMap<>(materials));

        Set<String> emissive = new HashSet<>();
        for (Map.Entry<String, BlockState> s : materials.entrySet()) {
            if (s.getValue().emissiveRendering(world, pos))
                emissive.add(s.getKey());
        }
        builder.with(EMISSIVE_PROPERTY, emissive);

        if (!(state.getBlock() instanceof ICopycatBlock copycatBlock))
            return builder;

        if (copycatBlock instanceof IMultiStateCopycatBlock multiStateBlock) {
            Map<String, ModelData> wrappedDataMap = new HashMap<>();
            Map<String, OcclusionData> occlusionMap = new HashMap<>();
            for (Map.Entry<String, BlockState> s : materials.entrySet()) {
                Vec3i inner = multiStateBlock.getVectorFromProperty(state, s.getKey());
                boolean enableCT = !(world.getBlockEntity(pos) instanceof IMultiStateCopycatBlockEntity);
                if ((world.getBlockEntity(pos) instanceof IMultiStateCopycatBlockEntity multiStateBE)) {
                    if (multiStateBE.getMaterialItemStorage().getMaterialItem(s.getKey()) != null)
                        try {
                            enableCT = multiStateBE.getMaterialItemStorage().getMaterialItem(s.getKey()).enableCT();
                            //Doing this as a double insurance
                        } catch (Exception ignored) {
                            enableCT = true;
                        }
                }
                ScaledBlockAndTintGetter scaledWorld = new ScaledBlockAndTintGetterForge(s.getKey(), world, pos, inner, multiStateBlock.vectorScale(state), p -> true);

                OcclusionData occlusionData = new OcclusionData();
                if (!VirtualRenderHelper.isVirtual(blockEntityData))
                    gatherOcclusionData(scaledWorld, pos, state, s.getValue(), occlusionData, copycatBlock);
                occlusionMap.put(s.getKey(), occlusionData);

                boolean finalEnableCT = enableCT;
                ScaledBlockAndTintGetter filteredWorld = new ScaledBlockAndTintGetterForge(s.getKey(), world, pos, inner, multiStateBlock.vectorScale(state),
                        targetPos -> {
                            if (!finalEnableCT) return false;
                            return multiStateBlock.canConnectTexturesToward(s.getKey(), scaledWorld, pos, targetPos, state);
                        });
                wrappedDataMap.put(s.getKey(), getModelOf(s.getValue()).getModelData(
                        filteredWorld,
                        pos, s.getValue(), ModelData.EMPTY));
            }
            return builder.with(OCCLUSION_PROPERTY, occlusionMap).with(WRAPPED_DATA_PROPERTY, wrappedDataMap);
        } else {
            BlockState material = materials.get(MATERIAL_KEY);
            if (material == null) return builder;

            OcclusionData occlusionData = new OcclusionData();
            if (!VirtualRenderHelper.isVirtual(blockEntityData))
                gatherOcclusionData(world, pos, state, material, occlusionData, copycatBlock);
            Map<String, OcclusionData> occlusionMap = Map.of(
                    MATERIAL_KEY,
                    occlusionData
            );
            builder.with(OCCLUSION_PROPERTY, occlusionMap);
            if (getModelOf(material) instanceof CTModel)
                builder.with(VARIANT_PROPERTY, copycatBlock.getModelVariant(world, state, pos));

            FilteredBlockAndTintGetter filteredWorld = new FilteredBlockAndTintGetterForge(world,
                    targetPos -> {
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof ICopycatBlockEntity copycatBE)
                            if (!copycatBE.isCTEnabled()) return false;
                        return copycatBlock.canConnectTexturesToward(world, pos, targetPos, state);
                    });
            Map<String, ModelData> wrappedDataMap = Map.of(
                    MATERIAL_KEY,
                    getModelOf(material).getModelData(
                            filteredWorld,
                            pos, material, ModelData.EMPTY)
            );
            PartialEdgeData partialEdges = gatherPartialEdges(world, filteredWorld, pos, state, material);
            if (partialEdges != null)
                builder.with(PARTIAL_EDGE_PROPERTY, partialEdges);
            return builder.with(WRAPPED_DATA_PROPERTY, wrappedDataMap);
        }
    }

    private @Nullable PartialEdgeData gatherPartialEdges(BlockAndTintGetter world, BlockAndTintGetter filteredWorld, BlockPos pos, BlockState state, BlockState material) {
        if (!(getModelOf(material) instanceof CTModel))
            return null;
        if (world.getBlockEntity(pos) instanceof ICopycatBlockEntity copycatBE && !copycatBE.isCTEnabled())
            return null;
        Map<Direction, List<AABB>> regions = BlockFaceUtils.getPartialEdgeRegions(world, pos, state, material);
        if (regions.isEmpty())
            return null;
        CopycatExternalContext.setPartialEdgeFaces(regions.keySet());
        try {
            return new PartialEdgeData(getModelOf(material).getModelData(filteredWorld, pos, material, ModelData.EMPTY), regions);
        } finally {
            CopycatExternalContext.setPartialEdgeFaces(null);
        }
    }

    private void gatherOcclusionData(BlockAndTintGetter world, BlockPos pos, BlockState state, BlockState material,
                                     CopycatModelNeoForge.OcclusionData occlusionData, ICopycatBlock copycatBlock) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (Direction face : Iterate.directions) {

            // Rubidium: Run an additional IForgeBlock.hidesNeighborFace check because it
            // seems to be missing in Block.shouldRenderFace
            BlockPos.MutableBlockPos neighbourPos = mutablePos.setWithOffset(pos, face);
            BlockState neighbourState = world.getBlockState(neighbourPos);
            if (state.supportsExternalFaceHiding() && neighbourState.hidesNeighborFace(world, neighbourPos, material, face.getOpposite())) {
                occlusionData.occlude(face);
                continue;
            }

            if (!Block.shouldRenderFace(state, world, pos, face, neighbourPos))
                occlusionData.occlude(face);
        }
    }

    protected @NotNull List<CopycatRenderContextNeoForge.CopycatBakedQuad> getQuads(BlockState state, @NotNull RandomSource rand, @NotNull ModelData data, RenderType renderType) {

        prepareModelCore(state, rand, data);

        List<CopycatRenderContextNeoForge.CopycatBakedQuad> allQuads = new ArrayList<>();
        Map<String, BlockState> materials = getMaterials(data);
        Map<String, OcclusionData> occlusionDataMap = getOcclusion(data);
        Map<String, ModelData> wrappedDataMap = getWrappedData(data);
        Set<String> emissive = getEmissive(data);
        final boolean isVirtual = VirtualRenderHelper.isVirtual(data);
        for (CopycatModelCore.ModelEntry entry : entries) {
            BlockState material = entry.materialMapper().map(state, materials.get(entry.key()));

            if (entry.type().onlyWhenVirtual() && !isVirtual)
                continue;
            if (entry.type().useCopycatLogic() && material == null) {
                // A new block entity has no model data until its onLoad, which can be after the first mesh
                if (materials.isEmpty() && state != null) {
                    material = AllBlocks.COPYCAT_BASE.getDefaultState();
                } else continue;
            }

            BakedModel model = getModelForEntry(entry, state, material);
            if (model == null) continue;

            BlockState wrappedState = state;
            ModelData wrappedData = data;
            if (entry.type().useCopycatLogic()) {
                wrappedState = material;
                wrappedData = wrappedDataMap.get(entry.key());
                if (wrappedData == null)
                    wrappedData = ModelData.EMPTY;
            }
            if (renderType != null) {
                if (!model.getRenderTypes(wrappedState, rand, wrappedData).contains(renderType))
                    continue;
            }
            if (VirtualRenderHelper.isVirtual(wrappedData) != isVirtual) {
                wrappedData = ModelDataUtils.mergeData(wrappedData, VirtualRenderHelper.VIRTUAL_DATA).build();
            }

            // Create's bars model retextures itself with the material it finds in its model data
            Direction barsFacing = entry.type().useCopycatLogic() && CopycatSpecialCases.isBarsMaterial(material) ? core.getBarsFacing(entry.key()) : null;
            if (barsFacing != null) {
                wrappedState = AllBlocks.COPYCAT_BARS.getDefaultState().setValue(DirectionalBlock.FACING, barsFacing);
                wrappedData = ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, material).build();
                model = getModelOf(wrappedState);
            }

            List<CopycatRenderContextNeoForge.CopycatBakedQuad> quads = getTemplateQuads(entry, model, wrappedState, rand, wrappedData, renderType);

            List<CopycatRenderContextNeoForge.CopycatBakedQuad> croppedQuads = getCroppedQuads(entry, state, quads, material, data);

            PartialEdgeData partialEdges = data.get(PARTIAL_EDGE_PROPERTY);
            if (partialEdges != null && entry.type().useCopycatLogic() && barsFacing == null) {
                List<CopycatRenderContextNeoForge.CopycatBakedQuad> connectedQuads = getTemplateQuads(entry, model, wrappedState, rand, partialEdges.data(), renderType);
                croppedQuads = PartialEdgeQuads.merge(croppedQuads, getCroppedQuads(entry, state, connectedQuads, material, data), partialEdges.regions());
            }

            CopycatModelNeoForge.OcclusionData occlusionData = occlusionDataMap.get(entry.key());
            for (CopycatRenderContextNeoForge.CopycatBakedQuad croppedQuad : croppedQuads) {
                if (occlusionData != null && occlusionData.isOccluded(croppedQuad.cullFace))
                    continue;

                // Copycat quads are already culled. Set it to null to avoid interference from sodium
                if (entry.type().useCopycatLogic())
                    croppedQuad.cullFace = null;

                // process() works on a copy: the vertices can be shared with the material's own model
                if (entry.type().useCopycatLogic() && emissive.contains(entry.key()))
                    croppedQuad = new CopycatRenderContextNeoForge.CopycatBakedQuad(QuadTransformers.settingMaxEmissivity().process(croppedQuad), croppedQuad.cullFace, croppedQuad.property);

                allQuads.add(croppedQuad);
            }
        }

        return allQuads;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, @NotNull RandomSource random) {
        return getQuads(state, direction, random, ModelData.EMPTY, null);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand, @NotNull ModelData data, RenderType renderType) {
        List<CopycatRenderContextNeoForge.CopycatBakedQuad> templateQuads = renderSession.get().getQuads(state, rand, data, renderType);
        Map<String, BlockState> materials = getMaterials(data);
        List<BakedQuad> quads = new ArrayList<>();
        for (CopycatRenderContextNeoForge.CopycatBakedQuad quad : templateQuads) {
            if (side != quad.cullFace)
                continue;
            quads.add(quad.toBakedQuad(materials.get(quad.property)));
        }
        return quads;
    }

    private static List<CopycatRenderContextNeoForge.CopycatBakedQuad> getTemplateQuads(CopycatModelCore.ModelEntry entry, BakedModel model, BlockState state, RandomSource rand, ModelData data, RenderType renderType) {
        List<CopycatRenderContextNeoForge.CopycatBakedQuad> quads = new ArrayList<>();
        for (Direction side : Iterate.directions) {
            for (BakedQuad templateQuad : model.getQuads(state, side, rand, data, renderType)) {
                quads.add(new CopycatRenderContextNeoForge.CopycatBakedQuad(templateQuad, side, entry.key()));
            }
        }
        for (BakedQuad templateQuad : model.getQuads(state, null, rand, data, renderType)) {
            quads.add(new CopycatRenderContextNeoForge.CopycatBakedQuad(templateQuad, null, entry.key()));
        }
        return quads;
    }

    private List<CopycatRenderContextNeoForge.CopycatBakedQuad> getCroppedQuads(CopycatModelCore.ModelEntry entry, BlockState state, List<CopycatRenderContextNeoForge.CopycatBakedQuad> templateQuads, BlockState material, ModelData data) {
        if (entry.part() == null)
            return templateQuads;
        List<CopycatRenderContextNeoForge.CopycatBakedQuad> quads = new ArrayList<>();
        Integer variant = data.get(VARIANT_PROPERTY);
        CopycatRenderContextNeoForge context = new CopycatRenderContextNeoForge(templateQuads, quads, entry.key(), variant == null ? 0 : variant);
        entry.part().emitCopycatQuads(entry.key(), state, context, material);
        return quads;
    }

    public BakedModel getModelForEntry(CopycatModelCore.ModelEntry entry, BlockState state, BlockState material) {
        if (entry.model() == null)
            return originalModel;
        else {
            if (core.colorize && state.getBlock() instanceof IMultiStateCopycatBlock multiState && AllBlocks.COPYCAT_BASE.has(material)) {
                material = CCBlocks.COPYCAT_BASE.getDefaultState().setValue(BASE_TYPE, multiState.getColorIndex(entry.key()) % BASE_TYPE_COUNT);
            }
            return entry.model().getModel(state, material);
        }
    }

    protected void prepareModelCore(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        core.prepareForRender();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        @NotNull Map<String, BlockState> material = getMaterials(data);

        if (material.isEmpty())
            return super.getParticleIcon(data);

        Map.Entry<String, BlockState> key = material.entrySet().stream()
                .filter(s -> !s.getValue().is(AllBlocks.COPYCAT_BASE.get()))
                .findFirst()
                .orElse(material.entrySet().iterator().next());

        return getModelOf(key.getValue()).getParticleIcon(getWrappedData(data).get(key.getKey()));
    }

    public static @NotNull BlockState getMaterial(ModelData data) {
        BlockState material = data == null ? null : data.get(MATERIAL_PROPERTY);
        return material == null ? AllBlocks.COPYCAT_BASE.getDefaultState() : material;
    }

    public static @NotNull Map<String, BlockState> getMaterials(ModelData data) {
        Map<String, BlockState> materials = data == null ? null : data.get(MATERIALS_PROPERTY);
        return materials == null ? Map.of() : materials;
    }

    public static @NotNull Map<String, OcclusionData> getOcclusion(ModelData data) {
        Map<String, OcclusionData> occlusions = data == null ? null : data.get(OCCLUSION_PROPERTY);
        return occlusions == null ? Map.of() : occlusions;
    }

    public static @NotNull Map<String, ModelData> getWrappedData(ModelData data) {
        Map<String, ModelData> wrappedData = data == null ? null : data.get(WRAPPED_DATA_PROPERTY);
        return wrappedData == null ? Map.of() : wrappedData;
    }

    public static @NotNull Set<String> getEmissive(ModelData data) {
        Set<String> emissive = data == null ? null : data.get(EMISSIVE_PROPERTY);
        return emissive == null ? Set.of() : emissive;
    }

    /**
     * The connected texture result with partial edges connected, and where on each face it replaces the normal one.
     */
    private record PartialEdgeData(ModelData data, Map<Direction, List<AABB>> regions) {
    }

    public static class OcclusionData {
        private final boolean[] occluded;

        public OcclusionData() {
            occluded = new boolean[6];
        }

        public void occlude(Direction face) {
            occluded[face.get3DDataValue()] = true;
        }

        public boolean isOccluded(Direction face) {
            return face != null && occluded[face.get3DDataValue()];
        }
    }

    @FunctionalInterface
    public interface Renderer {
        List<CopycatRenderContextNeoForge.CopycatBakedQuad> getQuads(BlockState state, @NotNull RandomSource rand, @NotNull ModelData data, RenderType renderType);
    }

    public static class RenderSession implements Renderer {
        private final Renderer renderer;
        private BlockState state = null;
        private RandomSource rand = null;
        private ModelData data = null;
        private RenderType renderType = null;
        private List<CopycatRenderContextNeoForge.CopycatBakedQuad> result = null;

        public RenderSession(Renderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public List<CopycatRenderContextNeoForge.CopycatBakedQuad> getQuads(BlockState state, @NotNull RandomSource rand, @NotNull ModelData data, RenderType renderType) {
            if (Objects.equals(this.state, state) && this.rand == rand && this.data == data && this.renderType == renderType && this.result != null) {
                return result;
            }
            this.state = state;
            this.rand = rand;
            this.data = data;
            this.renderType = renderType;
            this.result = renderer.getQuads(state, rand, data, renderType);
            return result;
        }
    }
}
