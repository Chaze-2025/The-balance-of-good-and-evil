# Network access and credentials

Downloads use HTTPS through the cloud environment's inherited proxy and trusted
CA configuration. Do not disable TLS checks, remove proxy settings, or extract
proxy-held credentials. Setup preserves checksum verification for the JDK and
Gradle distribution.

## Required build destinations

| Host | Purpose |
| --- | --- |
| `github.com`, `release-assets.githubusercontent.com` | Official Eclipse Temurin JDK release downloads and the existing Git remote |
| `raw.githubusercontent.com` | Official source/reference access when maintaining tooling |
| `services.gradle.org`, `downloads.gradle.org` | Pinned Gradle distribution and checksum |
| `plugins.gradle.org`, `plugins-artifacts.gradle.org` | Gradle plugin resolution where applicable |
| `repo.maven.apache.org`, `repo1.maven.org` | Maven Central dependencies |
| `maven.fabricmc.net` | Fabric Loader, Fabric API, Loom, Yarn, and tooling dependencies |
| `meta.fabricmc.net` | Official Fabric compatibility/version metadata |
| `piston-meta.mojang.com` | Official Minecraft version metadata |
| `piston-data.mojang.com` | Official Minecraft client/server development artifacts |
| `libraries.minecraft.net` | Minecraft libraries |

The environment already declares the five Fabric/Mojang hosts above as custom
allowlist entries. Gradle, Maven Central, GitHub, and standard package hosts are
covered by the package-manager preset. Preserve that preset and existing custom
hosts when editing access. The actual requested URL and redirects determine
whether another host is needed; report policy denials instead of silently
broadening access.

## Optional destinations

`resources.download.minecraft.net` supplies Minecraft client assets. A future
graphical client launch may also require Mojang/Microsoft session or account
services appropriate to the intended workflow; do not assume authentication is
configured. These are separate from compiling and packaging the mod.

`dl.cloudsmith.io` is GeckoLib's documented Maven repository. GeckoLib is not
installed; add its repository and network requirement only when implementing a
feature that needs it. Blockbench's browser application and documentation are
optional external tools, not required build infrastructure.

## GitHub integration

The existing Git checkout uses the platform's HTTPS Git integration. A read-only
`git ls-remote origin HEAD` succeeded during inspection. No new GitHub secret is
required for this build. That check establishes repository read access only, not
push permission, GitHub API authorization, or private package access. Do not
publish or push changes unless the user requests that action.

Creating pull requests and inspecting GitHub Actions through `gh` also requires
`api.github.com`, which is separate from HTTPS Git access to `github.com`.
If the proxy denies that host, add it in environment settings while preserving
the existing build destinations. A Git push can succeed while the API remains
blocked; verify the remote commit separately from PR/Actions status.

Inspect variable names/presence rather than values when diagnosing credentials.
Keep credentials out of scripts, `gradle.properties`, logs, and committed files.
Use secure environment settings for any genuinely required future credential.

## Debugging and services

Ordinary builds need no incoming network access or persistent service. Keep Java
debugging on loopback. A remotely reachable game server, multiplayer client,
remote desktop, or streaming service is outside this setup and requires its own
explicit network design and validation. Onboarding does not provide a supported
web preview for loopback Minecraft or debugger ports.
