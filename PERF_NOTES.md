# Performance notes

## P1. Spark baseline: copycat slabs against vanilla slabs (Nov 2, 2026)

Code: `b6a3f29e` (`experimental`). Run unattended with `runClient`; the profile files are not kept in the repo.

### Setup

- Machine: Ryzen 7 9800X3D (16 threads), RTX 5080, Windows 11, Java 21.0.11 (HotSpot).
- NeoForge 21.1.219, Sodium 0.8.13 (10 chunk builder threads, defer mode `ALWAYS`), Iris loaded with shaders off, spark 1.10.124.
- Render distance 12, windowed, `pauseOnLostFocus:false`, FOV setting 110, max FPS 260, no vsync.
- World: a copy of the dev superflat world. Three 100 by 50 areas, one layer at y = -40, 2,000 blocks or more apart:
  - A: 5,000 `copycats:copycat_slab[type=bottom]` with `minecraft:smooth_stone` in the bottom part (CT on).
  - B: 5,000 `minecraft:smooth_stone_slab[type=bottom]`.
  - C: empty, a control for the flat ground around each area.
  - A fourth empty spot, N, is where the camera goes between visits.
- A datapack placed the blocks with `/fill`. The log confirmed 5,000 blocks in A and B and the material in the block entity NBT on every run.
- Camera: spectator, 64 blocks above the centre of the area, looking straight down, so the whole area is in view. Sodium only meshes sections in the frustum.
- Per area: `spark profiler start --thread * --interval 1` (Java sampler, 1 ms; async-profiler isn't supported on Windows), then 3 times: teleport to the area, wait 10 s, teleport to N, wait 10 s. Then `spark profiler stop --save-to-file`, with no upload.
- Before profiling, each launch visited every area once to generate the chunks and warm up the JIT.
- Three launches, area order rotated: ABC, BCA, CAB. Runs 2 and 3 started from a fresh copy of the world. Run 1 reused the world from a trial launch.
- "Meshing" is the sampled time under Sodium's `ChunkBuilderMeshingTask`, summed over all builder threads. It is CPU time across threads, not wall time. Each profile has 3 × 5,000 block meshes for the slab area, plus the flat ground around it.

### Results (ms)

| Run | Meshing A (copycat) | Meshing B (vanilla) | Meshing C (empty) | A / B | A − B | Copycat methods in A's meshing |
|---|---|---|---|---|---|---|
| 1 (ABC) | 6638 | 5807 | 6201 | 1.14 | 831 | 596 |
| 2 (BCA) | 6582 | 6088 | 6092 | 1.08 | 494 | 772 |
| 3 (CAB) | 6202 | 5786 | 6363 | 1.07 | 416 | 691 |
| Trial | 6632 | 5795 | 5737 | 1.14 | 837 | 793 |

- Whole meshing, A against B: **1.07 to 1.14 times**. A − B is 0.42 to 0.83 s.
- Most meshing time goes to the flat ground. The control C ranges from 5.7 to 6.4 s, and in two runs it is above B. Differences between areas of about ±0.4 s are noise, so vanilla slabs' own meshing cost can't be told apart from the ground here.
- The time spent inside copycat methods during meshing is steadier: **0.60 to 0.79 s per 15,000 copycat slab meshes, about 40 to 51 µs CPU per block**. B and C spend 0 ms there.
- Outside meshing, A also spends 0.09 to 0.17 s on the render thread (block entity reads, `getShape`, model data refresh) and 0.12 to 0.25 s on the server thread (block entity load and save, `getShape`). These are chunk loading costs, not meshing.

### Where the copycat meshing time goes (area A)

| Method | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| `CopycatModelNeoForge.gatherModelData` | 445 | 429 | 516 |
| `CopycatModelNeoForge.getQuads` | 133 | 323 | 150 |
| …of which `getCroppedQuads` | 88 | 210 | 97 |
| `CopycatModelNeoForge.getRenderTypes` | 12 | 11 | 13 |

Top copycat methods under meshing, inclusive, summed over the trial and runs 1 to 3:

| ms | Method |
|---|---|
| 1988 | `CopycatModelNeoForge.gatherModelData` |
| 1747 | `CopycatModelNeoForge.gatherOcclusionData` |
| 919 | `ICopycatBlock.hidesNeighborFace` (`CopycatSlabBlock`) |
| 892 | `BlockFaceUtils.canOcclude` / `processBlockFace` |
| 754 | `CopycatModelNeoForge.getQuads` |
| 494 | `CopycatModelNeoForge.getCroppedQuads` (`CopycatMultiSlabModelCore.emitCopycatQuads`) |
| 460 | `CopycatRenderContextNeoForge.assemblePiece` |
| 245 | `CopycatSlabBlock.vectorScale` |
| 226 | `IMultiStateCopycatBlock.getPropertyFromRender` |

Sodium calls `gatherModelData` from the builder threads (`BakedModelWrapperWithData.getModelData`). `gatherModelData` is 56 to 75% of the copycat meshing time, and nearly all of that is occlusion data (`gatherOcclusionData` → `hidesNeighborFace`). Building quads (`getQuads`) is 22 to 42%.

### Other shapes and a CT material (second session, same night)

Same setup, code and settings. Each launch places every area below and profiles them in a rotated order. Two sets of three launches; every area is in one set except S and C, which are in both. Steam 'n' Rails isn't in the dev run, so Create's andesite casing stands in for locometal as the CT material.

- S: `copycat_slope[facing=north,shape=straight]`, smooth stone (rows of straight slopes).
- T: `copycat_stairs[facing=north,shape=straight]`, smooth stone.
- K: `copycat_slope`, all `outer_left`, smooth stone. `/fill` recomputes slope shapes from neighbours, so K is a pinwheel of 2 by 2 tiles, each slope facing the next one counterclockwise, set by hand and spread with `/clone`. The log checked `shape=outer_left` at the centre and both far corners.
- L: as S with `create:andesite_casing` (CT on).
- M: as K with `create:andesite_casing`.

Copycat methods inside meshing per profile (15,000 block meshes), range over three launches:

| Area | Copycat time (ms) | Per block (µs CPU) | `getQuads` (ms) | `gatherModelData` (ms) | Meshing over empty C (s) |
|---|---|---|---|---|---|
| A slab, smooth stone (this session) | 405–502 | 27–33 | 78–94 | 307–406 | 0.2–0.6 |
| S slope, smooth stone | 284–356 | 19–24 | 231–279 | 48–66 | 0.2–0.7 |
| T stairs, smooth stone | 268–326 | 18–22 | 202–271 | 51–61 | 0.0–0.5 |
| K corner slope, smooth stone | 906–1203 | 60–80 | 846–1096 | 55–96 | 0.7–1.5 |
| L slope, andesite casing | 4620–4978 | 308–332 | 549–670 | 3990–4357 | 4.5–5.1 |
| M corner slope, andesite casing | 3526–3570 | 235–238 | 1079–1249 | 2263–2440 | 3.5–3.8 |

The empty control C took 5.98 to 6.22 s across the six launches. With casing slopes in view, whole meshing goes up to about 10.6–11.1 s, about 1.75 times the empty control.

- Plain materials are cheap except on corners. Slopes and stairs cost about 20 µs per block, mostly `getQuads`. Corners cost three to four times that: `CopycatSlopeModelCore.assembleCorner` → `assembleTriangularSlope`.
- The slab's cost in the first session is mostly occlusion data. That path is the multi-state slab's own (`CopycatSlabBlock.hidesNeighborFace`). Slopes and stairs spend only about 50–100 ms there.
- CT materials are where the time goes, about 10 to 16 times a plain slope. Under L's `gatherModelData`, averaged per profile:

| ms | Method |
|---|---|
| 2583 | `CopycatModelNeoForge.gatherPartialEdges` |
| 2151 | `BlockFaceUtils.getEdge` |
| 1925 | `ICopycatBlock.checkConnection` |
| 1642 | `ICopycatBlock.getAppearance` (`CCCopycatBlock`) |
| 1609 | `CopycatSlopeBlock.isIgnoredConnectivitySide` |
| 1364 | `BlockFaceUtils.edgeMatch` |
| 1226 | `BlockFaceUtils.getPartialEdgeRegions` |

  Inclusive times overlap, so they don't add up. `gatherPartialEdges`, the fork's partial edge CT, is over half of L's copycat time and about 40% of M's.

### Not covered

- No run without Sodium: Iris is on the dev runtime and needs Sodium.
- Shaders were off.
- Locometal itself (Steam 'n' Rails isn't in the dev run), CT off on a CT material, inner corners, slope layers, vertical forms.
- Mixed builds: each area is one shape and one material, so CT connections only meet copies of the same block.
- Contraption assembly and rendering.
- The Java sampler has safepoint bias. 1 ms samples over about 0.7 s of copycat time are a few hundred samples per method row.
- The window wasn't watched. The frame rate in the background window and the GPU upload were not checked.
