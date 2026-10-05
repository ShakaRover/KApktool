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
package brut.androlib

import brut.androlib.exceptions.AndrolibException
import brut.androlib.meta.ApkInfo
import brut.androlib.meta.SdkInfo
import brut.androlib.res.AaptInvoker
import brut.androlib.res.AaptManager
import brut.androlib.res.data.ResChunkHeader
import brut.androlib.res.xml.ResXmlUtils
import brut.androlib.smali.SmaliBuilder
import brut.common.BrutException
import brut.common.Log
import brut.directory.Directory
import brut.directory.DirectoryException
import brut.directory.ExtFile
import brut.directory.FileDirectory
import brut.directory.ZipRODirectory
import brut.util.BackgroundWorker
import brut.util.BinaryDataInputStream
import brut.util.BrutIO
import brut.util.OS
import brut.util.ZipUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipOutputStream

/**
 * APK 构建流水线总控（与 ApkDecoder 对偶）：
 * smali->dex、aapt2 编译/链接资源、original 与 unknown 文件回填，最终打包 APK。
 *
 * 全量增量：源文件不比输出新则跳过；多 worker 并发汇编 smali，
 * 首个异常记录后跳过后续任务；manifest 编辑前均先备份 .orig 并在结束后还原。
 */
class ApkBuilder(
    apkFile: File,
    private val mConfig: Config,
) {
    /** 工程目录（解包结果）。 */
    private val mApkDir: ExtFile = ExtFile(apkFile)

    private val mFirstError = AtomicReference<AndrolibException?>()

    private var mApkInfo: ApkInfo? = null
    private var mSmaliBuilder: SmaliBuilder? = null
    private var mAaptInvoker: AaptInvoker? = null
    private var mWorker: BackgroundWorker? = null

    /** 构建整个工程；[outApk] 为 null 时输出到 dist/ 默认路径。 */
    @Throws(AndrolibException::class)
    fun build(outApkArg: File?) {
        if (mConfig.jobs > 1) {
            mWorker = BackgroundWorker(mConfig.jobs - 1)
        }
        var outApk = outApkArg
        try {
            val info = ApkInfo.load(File(mApkDir, "apktool.yml"))
            mApkInfo = info
            val smaliBuilder = SmaliBuilder(info.sdkInfo.minSdkVersionInt)
            mSmaliBuilder = smaliBuilder
            val aaptInvoker = AaptInvoker(info, mConfig)
            mAaptInvoker = aaptInvoker

            var apkName = info.apkFileName
            if (apkName == null) {
                apkName = "out.apk"
            }
            if (mConfig.isNoApk) {
                outApk = null
            } else if (outApk == null) {
                outApk = File(mApkDir, "dist/$apkName")
            }

            val buildDir = File(mApkDir, "build")
            val outDir = File(buildDir, "apk")
            if (mConfig.isForced) {
                OS.rmdir(buildDir)
            }
            OS.mkdir(outDir)

            Log.i(TAG, "Using Apktool " + mConfig.version + " on " + apkName +
                (if (mWorker != null) " with " + mConfig.jobs + " threads" else ""))

            buildSources(outDir)
            buildResources(outDir)

            mWorker?.let { worker ->
                worker.waitForFinish()
                mFirstError.get()?.let { throw it }
            }

            copyOriginalFiles(outDir)
            if (outApk != null) {
                buildApkFile(outDir, outApk)
            }
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        } finally {
            mWorker?.shutdownNow()
        }
    }

    /** 汇编 smali 目录并拷贝原始 dex。 */
    @Throws(AndrolibException::class)
    private fun buildSources(outDir: File) {
        try {
            val `in` = mApkDir.getDirectory()

            // 拷贝原始 dex。
            val dexFiles = HashSet<String>()
            for (fileName in `in`.getFiles()) {
                if (fileName.endsWith(".dex")) {
                    copySourcesRaw(outDir, fileName)
                    dexFiles.add(fileName)
                }
            }

            // 汇编 smali 目录。
            for (dirName in `in`.getDirs().keys) {
                var fileName: String = if (dirName == "smali") {
                    "classes.dex"
                } else if (dirName.startsWith("smali_")) {
                    val derived = dirName.substring(dirName.indexOf('_') + 1)
                        .replace('@', File.separatorChar) + ".dex"
                    try {
                        BrutIO.sanitizePath(outDir, derived)
                    } catch (ex: InvalidPathException) {
                        Log.w(TAG, "Smali folder name resolves to invalid dex path: %s -> %s", dirName, derived)
                        continue
                    }
                } else {
                    continue
                }

                if (!dexFiles.contains(fileName)) {
                    buildSourcesSmali(outDir, dirName, fileName)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun copySourcesRaw(outDir: File, fileName: String) {
        val inFile = File(mApkDir, fileName)
        val outFile = File(outDir, fileName)
        if (!isFileNewer(inFile, outFile)) {
            Log.i(TAG, "$fileName has not changed.")
            return
        }

        Log.i(TAG, "Copying raw $fileName...")
        try {
            BrutIO.copyAndClose(
                Files.newInputStream(inFile.toPath()),
                Files.newOutputStream(outFile.toPath())
            )
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun buildSourcesSmali(outDir: File, dirName: String, fileName: String) {
        val worker = mWorker
        if (worker != null) {
            worker.submit {
                if (mFirstError.get() == null) {
                    try {
                        buildSourcesSmaliJob(outDir, dirName, fileName)
                    } catch (ex: AndrolibException) {
                        mFirstError.compareAndSet(null, ex)
                    }
                }
            }
        } else {
            buildSourcesSmaliJob(outDir, dirName, fileName)
        }
    }

    @Throws(AndrolibException::class)
    private fun buildSourcesSmaliJob(outDir: File, dirName: String, fileName: String) {
        val smaliDir = File(mApkDir, dirName)
        val dexFile = File(outDir, fileName)
        if (!isFileNewer(smaliDir, dexFile)) {
            Log.i(TAG, "$dirName has not changed.")
            return
        }

        Log.i(TAG, "Smaling $dirName folder into $fileName...")
        mSmaliBuilder!!.build(smaliDir, dexFile)
    }

    /** 按 manifest/arsc 形态决定 raw 拷贝或 aapt2 构建。 */
    @Throws(AndrolibException::class)
    private fun buildResources(outDir: File) {
        val manifest = File(mApkDir, "AndroidManifest.xml")
        if (!manifest.isFile) {
            return
        }

        // 检测 manifest 是否为二进制 XML。
        val isBinaryManifest: Boolean
        BinaryDataInputStream(Files.newInputStream(manifest.toPath())).use { `in` ->
            isBinaryManifest = ResChunkHeader.read(`in`).type == ResChunkHeader.RES_XML_TYPE
        }

        // 二进制 manifest 直接原样拷贝。
        if (isBinaryManifest) {
            copyManifestRaw(outDir, manifest)
        }

        // 有现成 arsc 则整体原样拷贝。
        val arscFile = File(mApkDir, "resources.arsc")
        if (arscFile.isFile) {
            copyResourcesRaw(outDir, arscFile)
            return
        }

        // 二进制 manifest 无法参与构建。
        if (isBinaryManifest) {
            return
        }

        // 无资源目录时只构建 manifest。
        val resDir = File(mApkDir, "res")
        if (!resDir.isDirectory) {
            buildManifestOnly(outDir, manifest)
            return
        }

        buildResourcesFully(outDir, manifest, resDir)
    }

    @Throws(AndrolibException::class)
    private fun copyManifestRaw(outDir: File, manifest: File) {
        if (!isFileNewer(manifest, File(outDir, "AndroidManifest.xml"))) {
            Log.i(TAG, "AndroidManifest.xml has not changed.")
            return
        }

        Log.i(TAG, "Copying raw AndroidManifest.xml...")
        try {
            val `in`: Directory = mApkDir.getDirectory()
            `in`.copyToDir(outDir, "AndroidManifest.xml")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun copyResourcesRaw(outDir: File, arscFile: File) {
        if (!isFileNewer(arscFile, File(outDir, "resources.arsc"))) {
            Log.i(TAG, "resources.arsc has not changed.")
            return
        }

        Log.i(TAG, "Copying raw resources.arsc...")
        try {
            val `in`: Directory = mApkDir.getDirectory()
            `in`.copyToDir(outDir, "resources.arsc")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 备份 manifest -> 修补 -> aapt2 link -> 抽取 manifest -> 还原。 */
    @Throws(AndrolibException::class)
    private fun buildManifestOnly(outDir: File, manifest: File) {
        if (!isFileNewer(manifest, File(outDir, "AndroidManifest.xml"))) {
            Log.i(TAG, "AndroidManifest.xml has not changed.")
            return
        }

        // 备份 manifest 以便编辑。
        val manifestOrig = File(manifest.path + ".orig")
        try {
            OS.cpfile(manifest, manifestOrig)
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }

        ResXmlUtils.fixingPublicAttrsInProviderAttributes(manifest)

        if (mConfig.isDebuggable) {
            Log.i(TAG, "Setting 'debuggable' attribute to 'true' in AndroidManifest.xml...")
            ResXmlUtils.setApplicationDebugTagTrue(manifest)
        }

        val tmpFile: File = try {
            File.createTempFile("APKTOOL", null).also { OS.rmfile(it) }
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }

        Log.i(TAG, "Building AndroidManifest.xml with " + AaptManager.getBinaryName() + "...")
        mAaptInvoker!!.invoke(tmpFile, manifest, null)

        try {
            ZipRODirectory(tmpFile).use { tmpDir ->
                tmpDir.copyToDir(outDir, "AndroidManifest.xml")
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } finally {
            OS.rmfile(tmpFile)
        }

        // 还原原始 manifest。
        try {
            OS.mvfile(manifestOrig, manifest)
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }
    }

    /** 完整资源构建：aapt2 compile+link，含网络配置注入。 */
    @Throws(AndrolibException::class)
    private fun buildResourcesFully(outDir: File, manifest: File, resDir: File) {
        if (!isFileNewer(manifest, File(outDir, "AndroidManifest.xml")) &&
            !isFileNewer(resDir, File(outDir, "res"))
        ) {
            Log.i(TAG, "AndroidManifest.xml and resources have not changed.")
            return
        }

        // 备份 manifest 以便编辑。
        val manifestOrig = File(manifest.path + ".orig")
        try {
            OS.cpfile(manifest, manifestOrig)
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }

        ResXmlUtils.fixingPublicAttrsInProviderAttributes(manifest)

        if (mConfig.isDebuggable) {
            Log.i(TAG, "Setting 'debuggable' attribute to 'true' in AndroidManifest.xml...")
            ResXmlUtils.setApplicationDebugTagTrue(manifest)
        }

        if (mConfig.isNetSecConf) {
            Log.i(TAG, "Adding permissive network security config in manifest...")
            val netSecConfOrig = File(mApkDir, "res/xml/network_security_config.xml")
            OS.mkdir(netSecConfOrig.parentFile!!)
            ResXmlUtils.modNetworkSecurityConfig(netSecConfOrig)
            ResXmlUtils.setNetworkSecurityConfig(manifest)

            if (mApkInfo!!.sdkInfo.targetSdkVersionInt < SdkInfo.SDK_NOUGAT) {
                Log.w(TAG, "Target SDK version is lower than 24, Network Security Configuration might be ignored!")
            }
        }

        val tmpFile: File = try {
            File.createTempFile("APKTOOL", null).also { OS.rmfile(it) }
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }

        Log.i(TAG, "Building resources with " + AaptManager.getBinaryName() + "...")
        mAaptInvoker!!.invoke(tmpFile, manifest, resDir)

        try {
            ZipRODirectory(tmpFile).use { tmpDir ->
                tmpDir.copyToDir(outDir, "AndroidManifest.xml", "resources.arsc", "res")
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } finally {
            OS.rmfile(tmpFile)
        }

        // 还原原始 manifest。
        try {
            OS.mvfile(manifestOrig, manifest)
        } catch (ex: BrutException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun copyOriginalFiles(outDir: File) {
        if (!mConfig.isCopyOriginal) {
            return
        }

        val originalDir = File(mApkDir, "original")
        if (!originalDir.isDirectory) {
            return
        }

        Log.i(TAG, "Copying original files...")
        try {
            val in2 = FileDirectory(originalDir)

            for (fileName in in2.getFiles(true)) {
                if (ApkInfo.ORIGINAL_FILES_PATTERN.matcher(fileName).matches()) {
                    in2.copyToDir(outDir, fileName)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 把构建产物与 raw/unknown 文件统一压入目标 APK。 */
    @Throws(AndrolibException::class)
    private fun buildApkFile(outDir: File, outApk: File) {
        if (outApk.exists()) {
            OS.rmfile(outApk)
        } else {
            val parentDir = outApk.parentFile
            if (parentDir != null) {
                OS.mkdir(parentDir)
            }
        }

        // 转为 Set 以便快速判定。
        val doNotCompress: Set<String> = mApkInfo!!.doNotCompress.filterNotNull().toSet()

        Log.i(TAG, "Building apk file...")
        try {
            ZipOutputStream(Files.newOutputStream(outApk.toPath())).use { out ->
                // 打包 aapt2 输出。
                ZipUtils.zipDir(outDir, out, doNotCompress)

                // 打包 assets/lib 等标准 raw 目录。
                for (dirName in ApkInfo.RAW_DIRS) {
                    val rawDir = File(mApkDir, dirName)
                    if (rawDir.isDirectory) {
                        Log.i(TAG, "Importing $dirName...")
                        ZipUtils.zipDir(mApkDir, dirName, out, doNotCompress)
                    }
                }

                // 打包 unknown 目录。
                val unknownDir = File(mApkDir, "unknown")
                if (unknownDir.isDirectory) {
                    Log.i(TAG, "Importing unknown files...")
                    ZipUtils.zipDir(unknownDir, out, doNotCompress)
                }
            }
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
        Log.i(TAG, "Built apk into: " + outApk.path)
    }

    /** 文件（目录递归）修改时间是否比参照新。 */
    private fun isFileNewer(file: File, reference: File): Boolean =
        !reference.exists() || BrutIO.recursiveModifiedTime(file) > BrutIO.recursiveModifiedTime(reference)

    companion object {
        private val TAG = ApkBuilder::class.java.name
    }
}
