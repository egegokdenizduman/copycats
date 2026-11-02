# Feature notes

## Faster CT model data (replaces P2, design for review)

### Problem

From `PERF_NOTES.md` (branch `perf/p1-baseline`): a copycat slope with `create:andesite_casing` costs about 320 µs of meshing CPU per block. A smooth stone slope costs about 20 µs. The time is in `CopycatModelNeoForge.gatherModelData`, average per profile of 15,000 casing slopes:

| ms | Where |
|---|---|
| 1420 | First CT build: `CTModel.createCTData` → `ConnectedTextureBehaviour.buildContext` → `testConnection` → `ICopycatBlock.checkConnection` |
| 1350 | Second CT build in `gatherPartialEdges`, all six faces, with partial edges connected |
| 1226 | `BlockFaceUtils.getPartialEdgeRegions`, almost all `getEdge` → `Shapes.join` |

Top self time is shape math: `IndirectMerger.<init>` (694 ms), `SubShape.isFull`, `BitSetDiscreteVoxelShape.firstFull`, `Shapes.createIndexMerger`, `Shapes.joinUnoptimized`.

Cause, checked in the sources:

- Copycats are registered with `noOcclusion()` (`CCBuilderTransformersImpl.copycat()`, as in Create). So vanilla's `BlockStateBase.getFaceOcclusionShape` has no per-state cache for them. Each call runs `Shapes.getFaceShape(getOcclusionShape(...), dir)` and returns a new shape.
- `BlockFaceUtils.invokeCachedOperation` keys its cache on `ShapeKey(VoxelShape, VoxelShape)`. `VoxelShape` has no `equals`, so the key is identity, and fresh shapes on every call mean the cache almost never hits. Every `faceMatch` and `canOcclude` joins shapes again, as does every `edgeMatch` (two `getEdge` joins plus a compare). This runs for each of the eight neighbours of each face in both CT builds.
- `getPartialEdgeRegions` makes 48 `getEdge` joins per block (6 sides × 4 faces × 2 blocks) plus `isNotchedEdge`. It also joins the overlap, all from scratch.
- The second CT build covers all six faces, but its result is only used inside the regions, which are on a few faces.

### Changes

1. **Cache face and edge shapes per block state.** In `BlockFaceUtils`, a `getFaceShape(level, state, pos, face)` returns one shape object per (state, face) for copycats of this mod. `getEdge` works the same way per (state, side, texture face), and so does the joined face shape in `isNotchedEdge`. Storage is a `ConcurrentHashMap<BlockState, VoxelShape[]>`, filled lazily, because meshing threads call it at the same time. Shapes are immutable and never change after registration, so nothing needs clearing on reload.
   - `processBlockFace` and `getPartialEdgeRegions` use it in place of `getFaceOcclusionShape`. Stable shape objects then make the existing `invokeCachedOperation` cache hit, so `faceMatch`, `canOcclude` and occlusion data get faster without further changes.
   - Only for `ICopycatBlock` states that aren't `IMultiStateCopycatBlock`. Multi-state parts already go through `getPartialFaceShape`. Other mods' blocks keep calling `getFaceOcclusionShape`, because their shapes may depend on the world.
   - Condition: the copycat's occlusion shape depends only on its state. I'll audit every copycat's `getShape` and `getOcclusionShape` before using it (23 classes; a first grep found no block entity or world reads). Any copycat that does read the world stays uncached.
   - Expected: most of the 1.2 s in `getPartialEdgeRegions` and most of the shape joins in both CT builds go away. This is the biggest and simplest step, and it isn't fork-only: upstream has the same identity cache.
2. **Second CT build only on faces that have regions.** `gatherPartialEdges` already knows the faces from `regions.keySet()`. It passes them to `CopycatExternalContext`, as a thread-local next to `connectPartialEdges`. A second `@WrapOperation` in `CTModelMixin`, on `ConnectedTextureBehaviour.getDataType` inside `createCTData`, returns null for other faces while it is set, which Create already treats as "no CT data for this face". `PartialEdgeQuads` only reads the second result inside the regions, so the output is the same.
   - Expected: the 1.35 s drops in proportion to the faces skipped. A row of straight slopes has regions on about two to four of six faces.

Step 1 goes first and is measured alone. Step 2 follows if the second build is still large.

### Step 1 result (Nov 2)

Done in `BlockFaceUtils`. All 23 copycat shape overrides depend on state only (ladder occlusion is empty, ghost block is a full cube). The first version used `computeIfAbsent`. Every block in a uniform area has the same state, and `computeIfAbsent` locks the bin when the key isn't its first node, so one run spent 2 s waiting. It now does a plain `get` first.

- Output: a fixed-seed scene of 2,064 single-material copycats was dumped by a temporary harness. It covers 19 shapes, 8 materials (casings, framed glass, planks, stone) and CT on and off. The harness hashed every quad for each side and render type after `gatherModelData`. Old and new code gave identical hashes, and two runs of the new code matched each other.
- Copycat time in meshing, per 15,000 block meshes, three launches before → after:

| Area | Before (ms) | After (ms) | `gatherModelData` before → after (ms) |
|---|---|---|---|
| S slope, smooth stone | 292–356 | 300–329 | 54–66 → 52–69 |
| K corner, smooth stone | 906–1203 | 1102–1175 | 55–96 → 71–84 |
| M corner, andesite casing | 3526–3570 | 2149–2329 | 2263–2440 → 1073–1171 |
| L slope, andesite casing | 4620–4978 | 2874–3176 | 3990–4299 → 2295–2574 |

  Casing slopes went from about 320 to about 200 µs per block. Whole meshing with casing slopes in view went from 10.6–11.1 s to 8.7–9.5 s (empty control 5.8–6.2 s). The second CT build (`gatherPartialEdges`) is still about 1.25 s of L's 3.0 s.

### Out of scope

- Plain-material corners (`assembleCorner`, about 70 µs per block, all `getQuads`). That is what the dropped quad cache would have covered. It can come back later if corners matter.
- Removing the first CT build or merging the two builds. They test different rules on every seam, and merging would change Create's `buildContext` behaviour.
- Multi-state copycats (the slab's occlusion path); a separate task if wanted.

### Checks

- Build passes. Benchmark again with the P1 setup (areas S, K, L, M and C, three launches), comparing copycat time and `gatherModelData` with `PERF_NOTES.md`.
- Output must be identical, so a harness that compares `CTData` indices and `PartialEdgeData` regions before and after over the saved test world (casing stairs, steps, slopes and slope layers, as in the partial edge work) would cover it without playing.
- User in game: casing slopes, stairs and vertical steps from the partial edge screenshots look unchanged, with CT on and off, Sodium and Iris. Placing and breaking next to them updates borders as before.
