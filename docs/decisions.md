# Toolchain and compatibility decisions

## Minecraft 1.21.1 and Java 21

Minecraft 1.21.1 is selected as a mature mod compatibility baseline. It supports
modern entities, navigation, networking, world generation, and the established
GeckoLib 4 animation ecosystem. At setup inspection (2026-10-22), the Mojang
version manifest identified 26.3 as the latest release; this project does not target
the newest Minecraft. Choose a different baseline only when the desired feature
or dependency justifies the migration cost.

The official 1.21.1 metadata requires Java 21. The setup pins Eclipse Temurin
21.0.12.1+1 from Adoptium's official GitHub release and verifies the publisher's
SHA-256 before using the archive. The preinstalled Java runtime alone was
insufficient because a JDK also supplies `javac`, `jar`, and debugging tools.

## Fabric and build tooling

| Dependency | Version | Evidence |
| --- | --- | --- |
| Fabric Loader | 0.19.5 | Fabric Meta lists a stable loader for 1.21.1 |
| Fabric API | 0.116.17+1.21.1 | Official Fabric Maven version targeting 1.21.1 |
| Yarn | 1.21.1+build.3 | Exact mappings build for Minecraft 1.21.1 |
| Fabric Loom | 1.10.5 | Fixed stable release; compatible Gradle plugin API baseline 8.12 |
| Gradle | 8.14.3 | Compatible with the selected Loom baseline and running on Java 21 |

Yarn's Meta `stable` flag is not treated as a claim that these mappings are a
Minecraft prerelease. The game version and exact mapping coordinate identify the
intended target. Stable fixed releases are used instead of Loom snapshots or
dynamic dependency ranges.

Fabric is suitable for the planned custom entities, server AI, synchronization,
animations, sounds, structures, and world systems. Fabric API provides lifecycle,
networking, interaction, and world integration hooks; vanilla Minecraft supplies
the underlying entity and registry systems. No requirement currently needs a
NeoForge-specific dependency or API, so a framework switch would introduce cost
without solving an identified limitation. Evaluate NeoForge if a future required
library only supports it or a concrete integration cannot be supported cleanly
with Fabric. Do not maintain two loaders speculatively.

## Optional animation library

GeckoLib's official Minecraft 1.21.1 branch identifies GeckoLib 4.9.3 with Java 21,
Fabric Loader 0.19.3, and Fabric API 0.116.12+1.21.1. This provides evidence of an
applicable Fabric/1.21.1 integration; the project's later compatible loader/API
pins still require a real dependency build and runtime test when GeckoLib is
added. It is evaluated but deliberately not installed because the minimal mod
does not yet have animated creatures.
The optional Cloudsmith artifact host returned a network policy denial during
research; GeckoLib dependency resolution and runtime integration are untested.

Blockbench can export vanilla Java entity models or GeckoLib model and animation
JSON with its appropriate plugin. Blender is useful for authoring and offline
processing; arbitrary Blender rigs/meshes are not automatically Minecraft or
GeckoLib assets. See [assets](assets.md) for the conversion boundaries.

## Official references

- [Minecraft version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Exact Minecraft 1.21.1 metadata, including Java requirement](https://piston-meta.mojang.com/v1/packages/1757c8b1fbfc6b638504e1f6cceb05893f7389d5/1.21.1.json)
- [Fabric Loader metadata for 1.21.1](https://meta.fabricmc.net/v2/versions/loader/1.21.1)
- [Yarn metadata for 1.21.1](https://meta.fabricmc.net/v2/versions/yarn/1.21.1)
- [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)
- [Fabric Loom 1.10.5 module/Gradle API metadata](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.10.5/fabric-loom-1.10.5.module)
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod)
- [Gradle 8.14.3 distribution checksum](https://services.gradle.org/distributions/gradle-8.14.3-bin.zip.sha256)
- [Official Temurin 21.0.12.1+1 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)
- [GeckoLib 1.21.1 version catalog](https://raw.githubusercontent.com/bernie-g/geckolib/1.21.1/gradle/libs.versions.toml)
- [GeckoLib 4 installation documentation](https://github.com/bernie-g/geckolib/wiki/Installation-(Geckolib4))

Actual build and runtime results are recorded separately in
[verification](verification.md). Compatibility metadata alone does not establish
that the mod or a graphical client executed successfully.
