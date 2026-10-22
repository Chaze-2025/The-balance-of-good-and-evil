# Phase 1 Java test dependency checksums

The Vigil Torch adds test-only JUnit Jupiter 5.10.1 and JUnit Platform 1.10.1.
Minecraft, Fabric, Yarn, Loom, Gradle, and Java version pins are unchanged.
These libraries are not bundled in the distributable mod.

## Verification provenance

On 2026-10-22, each artifact below was fetched independently over verified HTTPS
from both official Maven Central endpoints:

- `https://repo.maven.apache.org/maven2/`
- `https://repo1.maven.org/maven2/`

For every artifact, both endpoints returned identical artifact bytes. The
computed SHA-256 and SHA-512 matched the corresponding published `.sha256` and
`.sha512` sidecars at **both** endpoints. Published SHA-1 sidecars were also
checked as additional evidence; Gradle uses the recorded SHA-256. No fallback
to SHA-1-only validation was needed.

The 21 individually verified artifact entries were added to
`gradle/verification-metadata.xml`. Every pre-existing checksum, verification
setting, and local Loom-output trust rule was retained unchanged. No published
dependency received a verification exception. The regular strict build verifies
the downloaded dependency bytes against these recorded hashes.

The artifact URL is the Central endpoint followed by the group with dots
replaced by slashes, then `<artifact>/<version>/<filename>`. For example,
[JUnit API JAR](https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar)
has adjacent `.sha256` and `.sha512` publisher files.

| Artifact filename | SHA-256 |
| --- | --- |
| `apiguardian-api-1.1.2.jar` | `b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38` |
| `apiguardian-api-1.1.2.module` | `e08028131375b357d1d28734e9a4fb4216da84b240641cb3ef7e7c7d628223fc` |
| `apiguardian-api-1.1.2.pom` | `32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0` |
| `junit-jupiter-api-5.10.1.jar` | `60d5c398c32dc7039b99282514ad6064061d8417cf959a1f6bd2038cc907c913` |
| `junit-jupiter-api-5.10.1.module` | `f87f7311549f49fe24e6282a1165e907e017a91e979cadabfdf9bf54c3f7106f` |
| `junit-jupiter-api-5.10.1.pom` | `3caf594b13dae7107510dd998f4d9e9fa277305cdca21321fdd59199dc87cf3d` |
| `junit-jupiter-engine-5.10.1.jar` | `02930dfe495f93fe70b26550ace3a28f7e1b900c84426c2e4626ce020c7282d6` |
| `junit-jupiter-engine-5.10.1.module` | `54121e582ccb2d4625ec9fa54679436c903fa053c74097bbe45fdfb9ddb67fb8` |
| `junit-jupiter-engine-5.10.1.pom` | `307086dc5e708cb7590108685c040e08e08ade0ed4954930b2f33c06db53ea5e` |
| `junit-platform-commons-1.10.1.jar` | `7d9855ee3f3f71f015eb1479559bf923783243c24fbfbd8b29bed8e8099b5672` |
| `junit-platform-commons-1.10.1.module` | `56d37062fce27041f0b5a35f393c68c8ad23afe34b2d30ef8e07b04fc119d4dd` |
| `junit-platform-commons-1.10.1.pom` | `4aef35a65e951915a13d9f76d1faff09a78c343e52242faf352043b70c7f6f30` |
| `junit-platform-engine-1.10.1.jar` | `baa48e470d6dee7369a0a8820c51da89c1463279eda6e13a304d11f45922c760` |
| `junit-platform-engine-1.10.1.module` | `297893711b92cf495f747e35a3aacb48310b1d1341a45cd84d23f9431c52e29d` |
| `junit-platform-engine-1.10.1.pom` | `dd7d64fd9af6789cb03c50c2586c915bb86b1e2cc8c07fb974f1db0ef861640d` |
| `junit-platform-launcher-1.10.1.jar` | `ded414c504e88d02270331071969084e1b2fd9bcf8443f35d44da2c6e3301bc2` |
| `junit-platform-launcher-1.10.1.module` | `dd9703c744ea0b9e15bf8c9e3febccfc1e347016a55d71ef7ffa9b0bc4c40512` |
| `junit-platform-launcher-1.10.1.pom` | `ba7ae817040c72a3c26d363a47fc13648a1cd0c64e9a38180dde9818a1b50e33` |
| `opentest4j-1.3.0.jar` | `48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b` |
| `opentest4j-1.3.0.module` | `48bf1d6c8b5dc94f74652bd17900f654deb714350248cf5e8fca27b9090c8e0d` |
| `opentest4j-1.3.0.pom` | `9bf7cffc410f3e8372c2522578df9ca56d9d43bd937e30948706c232a943b355` |

## Scope

The direct test dependencies are `junit-jupiter-api:5.10.1`,
`junit-jupiter-engine:5.10.1`, and `junit-platform-launcher:1.10.1`.
Their transitive requirements are `junit-platform-commons:1.10.1`,
`junit-platform-engine:1.10.1`, `opentest4j:1.3.0`, and
`apiguardian-api:1.1.2`. Their published POMs, Gradle module metadata, and JARs
are covered above. The existing JUnit BOM 5.10.1 POM and module checksums remain
unchanged.

Run `./scripts/gradle.sh --no-daemon --dependency-verification strict clean build`
to exercise dependency verification and the project validation tasks. This
checksum investigation does not itself establish that Java tests or gameplay
passed; their execution results are recorded separately in
[verification.md](verification.md).
