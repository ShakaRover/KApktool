/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.apktool

import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.FrameworkNotFoundException
import brut.androlib.exceptions.InFileNotFoundException
import brut.androlib.exceptions.OutDirExistsException
import brut.androlib.res.AaptManager
import brut.androlib.res.Framework
import brut.util.OSDetection
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.util.Properties
import java.util.logging.ErrorManager
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * apktool 命令行入口：子命令 decode/build/if/cf/lf/pr/help/version 的分发与参数解析。
 *
 * 选项冲突按“后者被忽略并告警”处理；日志用自定义 Handler 输出（W/E 走 stderr），
 * 静态属性来自打包进 jar 的 apktool.properties 与 smali/baksmali properties。
 */
object Main {
    private enum class Verbosity { NORMAL, VERBOSE, QUIET }

    // 通用选项

    private val verboseOption: Option = Option.builder("v")
        .longOpt("verbose")
        .desc("Increase output verbosity.")
        .get()

    private val quietOption: Option = Option.builder("q")
        .longOpt("quiet")
        .desc("Suppress normal output.")
        .get()

    private val jobsOption: Option = Option.builder("j")
        .longOpt("jobs")
        .desc("Set the number of jobs to execute in parallel to <num>.")
        .hasArg()
        .argName("num")
        .type(Integer::class.java)
        .get()

    private val frameDirOption: Option = Option.builder("p")
        .longOpt("frame-path")
        .desc("Use framework files located in <dir>.")
        .hasArg()
        .argName("dir")
        .get()

    private val frameTagOption: Option = Option.builder("t")
        .longOpt("frame-tag")
        .desc("Use framework files tagged with <tag>.")
        .hasArg()
        .argName("tag")
        .get()

    private val libOption: Option = Option.builder("l")
        .longOpt("lib")
        .desc(
            "Use shared library <package> located in <file>.\n" +
                "Can be specified multiple times."
        )
        .hasArg()
        .argName("package:file")
        .get()

    // decode 选项

    private val decodeForceOption: Option = Option.builder("f")
        .longOpt("force")
        .desc("Force delete destination directory.")
        .get()

    private val decodeAllSrcOption: Option = Option.builder("a")
        .longOpt("all-src")
        .desc("Decode all sources in the apk (includes unknown dex files).")
        .get()

    private val decodeNoSrcOption: Option = Option.builder("s")
        .longOpt("no-src")
        .desc("Do not decode sources.")
        .get()

    private val decodeNoDebugInfoOption: Option = Option.builder()
        .longOpt("no-debug-info")
        .desc("Do not include debug info in sources (.local, .param, .line, etc.)")
        .get()

    private val decodeNoResOption: Option = Option.builder("r")
        .longOpt("no-res")
        .desc("Do not decode resources.")
        .get()

    private val decodeOnlyManifestOption: Option = Option.builder()
        .longOpt("only-manifest")
        .desc("Only decode AndroidManifest.xml without resources.")
        .get()

    private val decodeResResolveModeOption: Option = Option.builder()
        .longOpt("res-resolve-mode")
        .desc(
            "Set the resolve mode for resources to <mode>.\n" +
                "Possible values: 'default', 'greedy' or 'lazy'."
        )
        .hasArg()
        .argName("mode")
        .get()

    private val decodeKeepBrokenResOption: Option = Option.builder()
        .longOpt("keep-broken-res")
        .desc(
            "Use if there was an error and some resources were dropped, e.g.\n" +
                "\"Invalid resource config detected. Dropping resources\", but you\n" +
                "want to decode them anyway, even with errors. You will have to\n" +
                "fix them manually before building."
        )
        .get()

    private val decodeIgnoreRawValuesOption: Option = Option.builder()
        .longOpt("ignore-raw-values")
        .desc("Ignore raw attribute values in XML resource files.")
        .get()

    private val decodeMatchOriginalOption: Option = Option.builder()
        .longOpt("match-original")
        .desc("Keep files closest to original as possible (prevents rebuild).")
        .get()

    private val decodeNoAssetsOption: Option = Option.builder()
        .longOpt("no-assets")
        .desc("Do not decode assets.")
        .get()

    private val decodeOutputOption: Option = Option.builder("o")
        .longOpt("output")
        .desc("Output decoded files to <dir>. (default: apk.out)")
        .hasArg()
        .argName("dir")
        .get()

    // build 选项

    private val buildForceOption: Option = Option.builder("f")
        .longOpt("force")
        .desc("Skip changes detection and build all files.")
        .get()

    private val buildNoApkOption: Option = Option.builder()
        .longOpt("no-apk")
        .desc("Disable repacking of the built files into a new apk.")
        .get()

    private val buildNoCrunchOption: Option = Option.builder()
        .longOpt("no-crunch")
        .desc("Disable crunching of resource files during the build step.")
        .get()

    private val buildCopyOriginalOption: Option = Option.builder()
        .longOpt("copy-original")
        .desc("Copy original AndroidManifest.xml and META-INF. See project page for more info.")
        .get()

    private val buildDebuggableOption: Option = Option.builder()
        .longOpt("debuggable")
        .desc("Set android:debuggable to \"true\" in AndroidManifest.xml for the built apk.")
        .get()

    private val buildNetSecConfOption: Option = Option.builder()
        .longOpt("net-sec-conf")
        .desc("Add a generic network security configuration file to the built apk.")
        .get()

    private val buildAaptOption: Option = Option.builder()
        .longOpt("aapt")
        .desc("Use aapt2 binary located in <file>.")
        .hasArg()
        .argName("file")
        .get()

    private val buildOutputOption: Option = Option.builder("o")
        .longOpt("output")
        .desc("Output the built apk to <file>. (default: dist/name.apk)")
        .hasArg()
        .argName("file")
        .get()

    // framework 选项

    private val frameFrameDirOption: Option = Option.builder("p")
        .longOpt("frame-path")
        .desc("Set the path for framework files to <dir>.")
        .hasArg()
        .argName("dir")
        .get()

    private val frameFrameTagOption: Option = Option.builder("t")
        .longOpt("frame-tag")
        .desc("Suffix framework files with <tag>.")
        .hasArg()
        .argName("tag")
        .get()

    private val frameForceAllOption: Option = Option.builder("a")
        .longOpt("all")
        .desc("Include all framework files regardless of tag.")
        .get()

    private val generalOptions = Options()
    private val decodeOptions = Options()
    private val buildOptions = Options()
    private val installFrameworkOptions = Options()
    private val cleanFrameworksOptions = Options()
    private val listFrameworksOptions = Options()
    private val publicizeResourcesOptions = Options()

    private val props = Props()
    private val config = Config(props.getVersion())
    private var loadedOptions: Options? = null
    private var advancedMode = false

    /** 装载指定（或全部）选项集；advanced 决定是否展示高级选项。 */
    private fun loadOptions(options: Options?, advanced: Boolean) {
        loadedOptions = options
        advancedMode = advanced

        generalOptions.addOption(quietOption)
        generalOptions.addOption(verboseOption)

        if (options == null || options === decodeOptions) {
            decodeOptions.addOption(decodeAllSrcOption)
            decodeOptions.addOption(decodeForceOption)
            decodeOptions.addOption(decodeNoResOption)
            decodeOptions.addOption(decodeNoSrcOption)
            decodeOptions.addOption(decodeOutputOption)
            decodeOptions.addOption(frameDirOption)
            decodeOptions.addOption(frameTagOption)
            decodeOptions.addOption(jobsOption)
            decodeOptions.addOption(libOption)
            if (advanced) {
                decodeOptions.addOption(decodeIgnoreRawValuesOption)
                decodeOptions.addOption(decodeKeepBrokenResOption)
                decodeOptions.addOption(decodeMatchOriginalOption)
                decodeOptions.addOption(decodeNoAssetsOption)
                decodeOptions.addOption(decodeNoDebugInfoOption)
                decodeOptions.addOption(decodeOnlyManifestOption)
                decodeOptions.addOption(decodeResResolveModeOption)
            }
        }

        if (options == null || options === buildOptions) {
            buildOptions.addOption(buildForceOption)
            buildOptions.addOption(buildOutputOption)
            buildOptions.addOption(frameDirOption)
            buildOptions.addOption(jobsOption)
            buildOptions.addOption(libOption)
            if (advanced) {
                buildOptions.addOption(buildAaptOption)
                buildOptions.addOption(buildCopyOriginalOption)
                buildOptions.addOption(buildDebuggableOption)
                buildOptions.addOption(buildNetSecConfOption)
                buildOptions.addOption(buildNoApkOption)
                buildOptions.addOption(buildNoCrunchOption)
            }
        }

        if (options == null || options === installFrameworkOptions) {
            installFrameworkOptions.addOption(frameFrameDirOption)
            installFrameworkOptions.addOption(frameFrameTagOption)
        }

        if (options == null || options === cleanFrameworksOptions) {
            cleanFrameworksOptions.addOption(frameForceAllOption)
            cleanFrameworksOptions.addOption(frameFrameDirOption)
            cleanFrameworksOptions.addOption(frameFrameTagOption)
        }

        if (options == null || options === listFrameworksOptions) {
            listFrameworksOptions.addOption(frameForceAllOption)
            listFrameworksOptions.addOption(frameFrameDirOption)
            listFrameworksOptions.addOption(frameFrameTagOption)
        }
    }

    /** CLI 主入口。 */
    @JvmStatic
    @Throws(AndrolibException::class)
    fun main(args: Array<String>) {
        // Headless
        System.setProperty("java.awt.headless", "true")

        // Java 11+ 对 zip 的更严格校验会被应用用作反汇编阻碍手段；
        // 我们已有目录穿越与畸形 zip 头防护，这里放行。
        System.setProperty("jdk.nio.zipfs.allowDotZipEntry", "true")
        System.setProperty("jdk.util.zip.disableZip64ExtraFieldValidation", "true")

        if (!OSDetection.is64Bit()) {
            System.err.println("Warning: Apktool no longer supports 32-bit platforms.")
        }

        if (args.isEmpty()) {
            loadOptions(null, false)
            printUsage()
            return
        }

        val cmdName = args[0]
        val cmdArgs = args.copyOfRange(1, args.size)

        when (cmdName) {
            "d", "decode" -> cmdDecode(cmdArgs)
            "b", "build" -> cmdBuild(cmdArgs)
            "if", "install-framework" -> cmdInstallFramework(cmdArgs)
            "cf", "clean-frameworks" -> cmdCleanFrameworks(cmdArgs)
            "lf", "list-frameworks" -> cmdListFrameworks(cmdArgs)
            "pr", "publicize-resources" -> cmdPublicizeResources(cmdArgs)
            "h", "help", "-help", "--help" -> {
                loadOptions(null, true)
                printUsage()
            }
            "v", "version", "-version", "--version" -> printVersion()
            else -> {
                System.err.println("Unrecognized command: $cmdName")
                loadOptions(null, false)
                printUsage()
                System.exit(1)
            }
        }
    }

    /** 解析通用+命令选项，处理 verbose/quiet 冲突并初始化日志。 */
    private fun parseOptions(options: Options, args: Array<String>): CommandLine {
        loadOptions(options, true)

        val combinedOptions = Options()
        combinedOptions.addOptions(generalOptions)
        combinedOptions.addOptions(options)

        val cli: CommandLine = try {
            DefaultParser(false).parse(combinedOptions, args, false)
        } catch (ex: ParseException) {
            System.err.println(ex.message)
            printUsage()
            System.exit(1)
            return null!!
        }

        // 检查 verbose/quiet。
        var verbosity = Verbosity.NORMAL
        if (cli.hasOption(verboseOption)) {
            config.isVerbose = true
            verbosity = Verbosity.VERBOSE
        }
        if (cli.hasOption(quietOption)) {
            if (cli.hasOption(verboseOption)) {
                printOptionConflict(quietOption, verboseOption)
            } else {
                verbosity = Verbosity.QUIET
            }
        }
        setupLogging(verbosity)

        return cli
    }

    private fun cmdDecode(args: Array<String>) {
        val cli = parseOptions(decodeOptions, args)
        val argList = cli.argList
        val apkFile: File = when (argList.size) {
            0 -> {
                System.err.println("Input apk file was not specified.")
                System.exit(1)
                return
            }
            1 -> File(argList[0])
            else -> {
                System.err.println("Invalid arguments.")
                printUsage()
                System.exit(1)
                return
            }
        }

        if (cli.hasOption(jobsOption)) {
            config.jobs = cli.getOptionValue(jobsOption)!!.toInt()
        }
        if (cli.hasOption(frameDirOption)) {
            config.frameworkDirectory = cli.getOptionValue(frameDirOption)
        }
        if (cli.hasOption(frameTagOption)) {
            config.frameworkTag = cli.getOptionValue(frameTagOption)
        }
        if (cli.hasOption(libOption)) {
            val libraryFiles = config.libraryFiles
            for (entry in cli.getOptionValues(libOption)!!) {
                val parts = entry.split(":".toRegex(), 2)
                if (parts.size == 2) {
                    libraryFiles[parts[0]] = parts[1].split(",").toTypedArray()
                }
            }
        }
        if (cli.hasOption(decodeForceOption)) {
            config.isForced = true
        }
        if (cli.hasOption(decodeAllSrcOption)) {
            config.setDecodeSources(Config.DecodeSources.FULL)
        }
        if (cli.hasOption(decodeNoSrcOption)) {
            if (cli.hasOption(decodeAllSrcOption)) {
                printOptionConflict(decodeNoSrcOption, decodeAllSrcOption)
            } else {
                config.setDecodeSources(Config.DecodeSources.NONE)
            }
        }
        if (cli.hasOption(decodeNoDebugInfoOption)) {
            if (cli.hasOption(decodeNoSrcOption)) {
                printOptionConflict(decodeNoDebugInfoOption, decodeNoSrcOption)
            } else {
                config.isBaksmaliDebugMode = false
            }
        }
        if (cli.hasOption(decodeNoResOption)) {
            config.setDecodeResources(Config.DecodeResources.NONE)
        }
        if (cli.hasOption(decodeOnlyManifestOption)) {
            if (cli.hasOption(decodeNoResOption)) {
                printOptionConflict(decodeOnlyManifestOption, decodeNoResOption)
            } else {
                config.setDecodeResources(Config.DecodeResources.ONLY_MANIFEST)
            }
        }
        if (cli.hasOption(decodeResResolveModeOption)) {
            if (cli.hasOption(decodeNoResOption)) {
                printOptionConflict(decodeResResolveModeOption, decodeNoResOption)
            } else if (cli.hasOption(decodeOnlyManifestOption)) {
                printOptionConflict(decodeResResolveModeOption, decodeOnlyManifestOption)
            } else {
                val mode = cli.getOptionValue(decodeResResolveModeOption)
                when (mode) {
                    "default" -> config.setDecodeResolve(Config.DecodeResolve.DEFAULT)
                    "greedy" -> config.setDecodeResolve(Config.DecodeResolve.GREEDY)
                    "lazy" -> config.setDecodeResolve(Config.DecodeResolve.LAZY)
                    else -> {
                        System.err.println("Unknown resolve resources mode: $mode")
                        System.err.println("Expect: 'default', 'greedy' or 'lazy'.")
                        System.exit(1)
                        return
                    }
                }
            }
        }
        if (cli.hasOption(decodeKeepBrokenResOption)) {
            if (cli.hasOption(decodeNoResOption)) {
                printOptionConflict(decodeKeepBrokenResOption, decodeNoResOption)
            } else if (cli.hasOption(decodeOnlyManifestOption)) {
                printOptionConflict(decodeKeepBrokenResOption, decodeOnlyManifestOption)
            } else {
                config.isKeepBrokenResources = true
            }
        }
        if (cli.hasOption(decodeIgnoreRawValuesOption)) {
            if (cli.hasOption(decodeNoResOption)) {
                printOptionConflict(decodeIgnoreRawValuesOption, decodeNoResOption)
            } else {
                config.isIgnoreRawValues = true
            }
        }
        if (cli.hasOption(decodeMatchOriginalOption)) {
            config.isAnalysisMode = true
        }
        if (cli.hasOption(decodeNoAssetsOption)) {
            config.setDecodeAssets(Config.DecodeAssets.NONE)
        }

        val outDir: File = if (cli.hasOption(decodeOutputOption)) {
            File(cli.getOptionValue(decodeOutputOption)!!)
        } else {
            var outName: String = apkFile.name
            outName = if (outName.endsWith(".apk")) {
                outName.substring(0, outName.length - 4).trim()
            } else {
                outName + ".out"
            }
            File(apkFile.parentFile, outName)
        }

        try {
            ApkDecoder(apkFile, config).decode(outDir)
        } catch (ex: InFileNotFoundException) {
            System.err.println(ex.message)
            System.exit(1)
        } catch (ex: OutDirExistsException) {
            System.err.println(ex.message)
            System.exit(1)
        } catch (ex: FrameworkNotFoundException) {
            System.err.println(ex.message)
            System.exit(1)
        }
    }

    private fun cmdBuild(args: Array<String>) {
        val cli = parseOptions(buildOptions, args)
        val argList = cli.argList
        val apkDir: File = when (argList.size) {
            0 -> File(".") // 当前目录
            1 -> File(argList[0])
            else -> {
                System.err.println("Invalid arguments.")
                printUsage()
                System.exit(1)
                return
            }
        }

        if (cli.hasOption(jobsOption)) {
            config.jobs = cli.getOptionValue(jobsOption)!!.toInt()
        }
        if (cli.hasOption(frameDirOption)) {
            config.frameworkDirectory = cli.getOptionValue(frameDirOption)
        }
        if (cli.hasOption(libOption)) {
            val libraryFiles = config.libraryFiles
            for (entry in cli.getOptionValues(libOption)!!) {
                val parts = entry.split(":".toRegex(), 2)
                if (parts.size == 2) {
                    libraryFiles[parts[0]] = parts[1].split(",").toTypedArray()
                }
            }
        }
        if (cli.hasOption(buildForceOption)) {
            config.isForced = true
        }
        if (cli.hasOption(buildNoApkOption)) {
            config.isNoApk = true
        }
        if (cli.hasOption(buildNoCrunchOption)) {
            config.isNoCrunch = true
        }
        if (cli.hasOption(buildCopyOriginalOption)) {
            config.isCopyOriginal = true
        }
        if (cli.hasOption(buildDebuggableOption)) {
            if (cli.hasOption(buildCopyOriginalOption)) {
                printOptionConflict(buildDebuggableOption, buildCopyOriginalOption)
            } else {
                config.isDebuggable = true
            }
        }
        if (cli.hasOption(buildNetSecConfOption)) {
            if (cli.hasOption(buildCopyOriginalOption)) {
                printOptionConflict(buildNetSecConfOption, buildCopyOriginalOption)
            } else {
                config.isNetSecConf = true
            }
        }
        if (cli.hasOption(buildAaptOption)) {
            try {
                val aaptBinary = cli.getOptionValue(buildAaptOption)!!
                if (AaptManager.getBinaryVersion(File(aaptBinary)) == 1) {
                    throw AndrolibException("Legacy aapt is no longer supported.")
                }

                config.aaptBinary = aaptBinary
            } catch (ex: AndrolibException) {
                System.err.println(ex.message)
                System.exit(1)
            }
        }

        var outFile: File? = null
        if (cli.hasOption(buildOutputOption)) {
            if (cli.hasOption(buildNoApkOption)) {
                printOptionConflict(buildOutputOption, buildNoApkOption)
            } else {
                outFile = File(cli.getOptionValue(buildOutputOption)!!)
            }
        }

        ApkBuilder(apkDir, config).build(outFile)
    }

    private fun cmdInstallFramework(args: Array<String>) {
        val cli = parseOptions(installFrameworkOptions, args)
        val argList = cli.argList
        val apkFile: File = when (argList.size) {
            0 -> {
                System.err.println("Input apk file was not specified.")
                System.exit(1)
                return
            }
            1 -> File(argList[0])
            else -> {
                System.err.println("Invalid arguments.")
                printUsage()
                System.exit(1)
                return
            }
        }

        if (cli.hasOption(frameFrameDirOption)) {
            config.frameworkDirectory = cli.getOptionValue(frameFrameDirOption)
        }
        if (cli.hasOption(frameFrameTagOption)) {
            config.frameworkTag = cli.getOptionValue(frameFrameTagOption)
        }

        Framework(config).install(apkFile)
    }

    private fun cmdCleanFrameworks(args: Array<String>) {
        val cli = parseOptions(cleanFrameworksOptions, args)
        if (cli.argList.isNotEmpty()) {
            System.err.println("Invalid arguments.")
            printUsage()
            System.exit(1)
            return
        }

        if (cli.hasOption(frameFrameDirOption)) {
            config.frameworkDirectory = cli.getOptionValue(frameFrameDirOption)
        }
        if (cli.hasOption(frameFrameTagOption)) {
            config.frameworkTag = cli.getOptionValue(frameFrameTagOption)
        }
        if (cli.hasOption(frameForceAllOption)) {
            if (cli.hasOption(frameFrameTagOption)) {
                printOptionConflict(frameForceAllOption, frameFrameTagOption)
            } else {
                config.isForced = true
            }
        }

        Framework(config).cleanDirectory()
    }

    private fun cmdListFrameworks(args: Array<String>) {
        val cli = parseOptions(listFrameworksOptions, args)
        if (cli.argList.isNotEmpty()) {
            System.err.println("Invalid arguments.")
            printUsage()
            System.exit(1)
            return
        }

        if (cli.hasOption(frameFrameDirOption)) {
            config.frameworkDirectory = cli.getOptionValue(frameFrameDirOption)
        }
        if (cli.hasOption(frameFrameTagOption)) {
            config.frameworkTag = cli.getOptionValue(frameFrameTagOption)
        }
        if (cli.hasOption(frameForceAllOption)) {
            if (cli.hasOption(frameFrameTagOption)) {
                printOptionConflict(frameForceAllOption, frameFrameTagOption)
            } else {
                config.isForced = true
            }
        }

        for (file in Framework(config).listDirectory()) {
            println(file.name)
        }
    }

    private fun cmdPublicizeResources(args: Array<String>) {
        val cli = parseOptions(publicizeResourcesOptions, args)
        val argList = cli.argList
        val arscFile: File = when (argList.size) {
            0 -> {
                System.err.println("Input arsc file was not specified.")
                System.exit(1)
                return
            }
            1 -> File(argList[0])
            else -> {
                System.err.println("Invalid arguments.")
                printUsage()
                System.exit(1)
                return
            }
        }

        Framework(config).publicizeResources(arscFile)
    }

    private fun printOptionConflict(option: Option, conflict: Option) {
        System.err.println(
            "Ignoring " + formatOption(option) + " (cannot be used with " + formatOption(conflict) + ")"
        )
    }

    private fun formatOption(option: Option): String {
        val sb = StringBuilder()
        val shortName = option.getOpt()
        if (shortName != null) {
            sb.append('-').append(shortName)
        }
        val longName = option.getLongOpt()
        if (longName != null) {
            if (sb.isNotEmpty()) {
                sb.append('/')
            }
            sb.append("--").append(longName)
        }
        return sb.toString()
    }

    @Suppress("DEPRECATION")
    private fun printUsage() {
        val writer = PrintWriter(System.out)
        val formatter = HelpFormatter()

        // 头部。
        writer.println("Apktool " + props.getVersion() + " - a tool for reengineering Android apk files")
        writer.println("with smali " + props.getSmaliVersion() + " and baksmali " + props.getBaksmaliVersion())
        writer.println("Copyright 2010 Ryszard Wiśniewski <brut.alll@gmail.com>")
        writer.println("Copyright 2010 Connor Tumbleson <connor.tumbleson@gmail.com>")
        if (advancedMode) {
            writer.println("Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)")
        }
        writer.println()

        // 用法列表。
        writer.println("General options:")
        printOptions(writer, formatter, generalOptions)
        writer.println()
        if (loadedOptions == null || loadedOptions === decodeOptions) {
            writer.println("apktool d|decode [options] <apk-file>")
            printOptions(writer, formatter, decodeOptions)
            writer.println()
        }
        if (loadedOptions == null || loadedOptions === buildOptions) {
            writer.println("apktool b|build [options] <apk-dir>")
            printOptions(writer, formatter, buildOptions)
            writer.println()
        }
        if (loadedOptions == null || loadedOptions === installFrameworkOptions) {
            writer.println("apktool if|install-framework [options] <apk-file>")
            printOptions(writer, formatter, installFrameworkOptions)
            writer.println()
        }
        if (advancedMode && loadedOptions == null || loadedOptions === cleanFrameworksOptions) {
            writer.println("apktool cf|clean-frameworks [options]")
            printOptions(writer, formatter, cleanFrameworksOptions)
            writer.println()
        }
        if (advancedMode && loadedOptions == null || loadedOptions === listFrameworksOptions) {
            writer.println("apktool lf|list-frameworks [options]")
            printOptions(writer, formatter, listFrameworksOptions)
            writer.println()
        }
        if (advancedMode && loadedOptions == null || loadedOptions === publicizeResourcesOptions) {
            writer.println("apktool pr|publicize-resources <arsc-file>")
            printOptions(writer, formatter, publicizeResourcesOptions)
            writer.println()
        }
        if (loadedOptions == null) {
            writer.println("apktool h|help")
            writer.println()
            writer.println("apktool v|version")
            writer.println()
        }

        // 尾部。
        writer.println("For additional info, see: https://apktool.org")
        writer.println("For smali/baksmali info, see: https://github.com/google/smali")

        writer.flush()
    }

    @Suppress("DEPRECATION")
    private fun printOptions(writer: PrintWriter, formatter: HelpFormatter, options: Options) {
        val width = 120
        val leftPadding = 1
        val descPadding = 3

        if (options.options.isNotEmpty()) {
            formatter.printOptions(writer, width, options, leftPadding, descPadding)
        }
    }

    private fun printVersion() {
        println(props.getVersion())
    }

    /** 按输出级别重置并配置 JUL 日志（W/E 走 stderr）。 */
    private fun setupLogging(verbosity: Verbosity) {
        LogManager.getLogManager().reset()
        val logger = Logger.getLogger("")

        if (verbosity == Verbosity.QUIET) {
            logger.setLevel(Level.OFF)
            return
        }

        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                if (!isLoggable(record)) {
                    return
                }
                try {
                    val message = formatter.format(record)
                    val level = record.level.intValue()
                    if (level >= Level.WARNING.intValue()) {
                        System.err.println(message)
                    } else {
                        println(message)
                    }
                } catch (ex: Exception) {
                    reportError(null, ex, ErrorManager.FORMAT_FAILURE)
                }
            }

            override fun flush() {
                System.out.flush()
                System.err.flush()
            }

            override fun close() {
                flush()
            }
        }
        handler.setFormatter(object : Formatter() {
            override fun format(record: LogRecord): String {
                val prefix: String = when {
                    record.level.intValue() >= Level.SEVERE.intValue() -> "E"
                    record.level.intValue() >= Level.WARNING.intValue() -> "W"
                    record.level.intValue() >= Level.INFO.intValue() -> "I"
                    else -> "D"
                }
                return "$prefix: ${record.message}"
            }
        })
        logger.addHandler(handler)
        logger.level = if (verbosity == Verbosity.VERBOSE) Level.ALL else Level.INFO
    }

    /** 内置版本属性：apktool + smali/baksmali。 */
    private class Props : Properties() {
        init {
            load(this, "/apktool.properties")

            val smaliProps = Properties()
            load(smaliProps, "/smali.properties")
            val smaliVersion = smaliProps.getProperty("application.version", "")
            if (smaliVersion.isNotEmpty()) {
                put("smali.version", smaliVersion)
            }

            val baksmaliProps = Properties()
            load(baksmaliProps, "/baksmali.properties")
            val baksmaliVersion = baksmaliProps.getProperty("application.version", "")
            if (baksmaliVersion.isNotEmpty()) {
                put("baksmali.version", baksmaliVersion)
            }
        }

        /** apktool 版本号。 */
        fun getVersion(): String = getProperty("application.version", "(unknown)")

        /** smali 版本号。 */
        fun getSmaliVersion(): String = getProperty("smali.version", "(unknown)")

        /** baksmali 版本号。 */
        fun getBaksmaliVersion(): String = getProperty("baksmali.version", "(unknown)")

        private fun load(props: Properties, name: String) {
            try {
                Main::class.java.getResourceAsStream(name)?.use { `in` ->
                    props.load(`in`)
                } ?: throw FileNotFoundException(name)
            } catch (ignored: IOException) {
                System.err.println("Could not load resource: $name")
            }
        }
    }
}
