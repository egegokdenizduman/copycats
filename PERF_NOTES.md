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

### Not covered

- No run without Sodium: Iris is on the dev runtime and needs Sodium.
- Shaders were off.
- Connected-texture materials: smooth stone has none, so `CopycatExternalContext` and CT lookups barely show up.
- Only the slab and only one material. Shapes with more cropping (slopes, stairs, corners) were not measured.
- Contraption assembly and rendering.
- The Java sampler has safepoint bias. 1 ms samples over about 0.7 s of copycat time are a few hundred samples per method row.
- The window wasn't watched. The frame rate in the background window and the GPU upload were not checked.
