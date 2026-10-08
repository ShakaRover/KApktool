### Apktool

[![CI](https://github.com/ShakaRover/KApktool/actions/workflows/build.yml/badge.svg)](https://github.com/ShakaRover/KApktool/actions/workflows/build.yml)
[![Software License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](https://github.com/ShakaRover/KApktool/blob/main/LICENSE.md)

Apktool is a tool for reverse engineering third-party, closed, binary, Android apps. It can decode resources to nearly original form and rebuild them after making some modifications; it makes it possible to debug smali code step-by-step. It also makes working with apps easier thanks to project-like file structure and automation of some repetitive tasks such as building apk, etc.

Apktool is **NOT** intended for piracy and other non-legal uses. It could be used for localizing and adding features, adding support for custom platforms, and other GOOD purposes. Just try to be fair with the authors of an app, that you use and probably like.

_This repository is a fork of [iBotPeaches/Apktool](https://github.com/iBotPeaches/Apktool) that continues the Apktool 3.x line. It builds smali/baksmali from the [ksmali](https://github.com/ShakaRover/ksmali) git submodule instead of consuming the prebuilt Maven artifacts._

### Sub-tools
The smali and baksmali command line tools are bundled and exposed as apktool subcommands:

```
apktool smali assemble [options] <smali-dir>       # smali -> dex
apktool smali tokens <smali-file>                  # dump the lexer tokens (hidden command)
apktool baksmali disassemble [options] <dex-file>  # dex -> smali
apktool baksmali deodex [options] <odex-file>      # odex -> smali
apktool baksmali dump [options] <dex-file>         # dump the dex structure
apktool baksmali list [options] <dex-file>         # list classes/fields/methods
```

They behave exactly like the standalone `smali` / `baksmali` binaries; run
`apktool smali --help` or `apktool baksmali --help` for the full command list.

### Branches
- `main` - Apktool 3.x development, the only branch kept in this fork. The upstream `2.x` maintenance branches live at [iBotPeaches/Apktool](https://github.com/iBotPeaches/Apktool).

### Building
JDK 17 or newer is required. `./gradlew` defaults to `build shadowJar proguard` and writes the runnable jar to
`brut.apktool/apktool-cli/build/libs/apktool_<version>-SNAPSHOT.jar`, where `<version>` is the output of
`git describe --tags` (e.g. `apktool_v3.0.3-36-64f5f296-SNAPSHOT.jar`); the unshrunk jar of `shadowJar` alone is
`brut.apktool/apktool-cli/build/libs/apktool-cli-all.jar`. The CI matrix in `.github/workflows/build.yml` builds with
JDK 17 and runs the tests on JDK 8, 11, 17, 21 and 25 for Linux, macOS and Windows.

#### Security Vulnerabilities

If you discover a security vulnerability within Apktool, please send an e-mail to Connor Tumbleson at connor.tumbleson(at)gmail.com. All security vulnerabilities will be promptly addressed.

#### Links
- [Bug Reports](https://github.com/ShakaRover/KApktool/issues)
- [Source (GitHub)](https://github.com/ShakaRover/KApktool)
- [smali/baksmali source (ksmali)](https://github.com/ShakaRover/ksmali)
- [Upstream](https://github.com/iBotPeaches/Apktool)


## Sponsors

Special thanks goes to the following sponsors:

### Sourcetoad
[Sourcetoad](https://sourcetoad.com/) is an award-winning software and app development firm committed to the co-creation of technology solutions that solve complex business problems, delight users, and help our clients achieve their goals.

<a href="https://www.sourcetoad.com" alt="Sourcetoad">
    <picture>
        <img src=".github/assets/sponsors/sourcetoad-horizontal.svg">
    </picture>
</a>

### Emerge Tools

[Emerge Tools](https://www.emergetools.com) is a suite of revolutionary products designed to supercharge mobile apps and the teams that build them.

<a href="https://www.emergetools.com" alt="Emerge Tools">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset=".github/assets/sponsors/emerge-tools-vertical-white.svg">
        <source media="(prefers-color-scheme: light)" srcset=".github/assets/sponsors/emerge-tools-vertical-black.svg">
        <img src=".github/assets/sponsors/emerge-tools-vertical-black.svg">
    </picture>
</a>
