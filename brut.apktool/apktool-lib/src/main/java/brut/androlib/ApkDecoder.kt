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
import brut.androlib.exceptions.InFileNotFoundException
import brut.androlib.exceptions.OutDirExistsException
import brut.androlib.meta.ApkInfo
import brut.androlib.res.ResDecoder
import brut.androlib.smali.SmaliDecoder
import brut.common.Log
import brut.directory.Directory
import brut.directory.DirectoryException
import brut.directory.ExtFile
import brut.util.BackgroundWorker
import brut.util.BrutIO
import brut.util.OS
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * APK 解码流水线总控：源码（smali/raw）、资源（values/raw）、manifest、
 * original/raw/unknown 文件拷贝，最后写出 apktool.yml。
 *
 * 多任务模式下（jobs>1）smali 反汇编并发执行，任一失败记录首个异常并在收尾时抛出；
 * [writeApkInfo] 统计未压缩扩展名/文件生成 doNotCompress 清单。
 */
class ApkDecoder(
    apkFile: File,
    private val mConfig: Config,
) {
    /** 关联的 APK（带目录视图能力的 ExtFile）。 */
    private val mApkFile: ExtFile = ExtFile(apkFile)

    private val mFirstError = AtomicReference<AndrolibException?>()

    /** 解码得到的工程元数据。 */
    var apkInfo: ApkInfo? = null
        private set

    private var mSmaliDecoder: SmaliDecoder? = null
    private var mResDecoder: ResDecoder? = null
    private var mWorker: BackgroundWorker? = null

    /** 把 APK 完整解码到 [outDir]。 */
    @Throws(AndrolibException::class)
    fun decode(outDir: File) {
        if (!mApkFile.isFile || !mApkFile.canRead()) {
            throw InFileNotFoundException(mApkFile.path)
        }
        // 出于安全考虑不跟随符号链接。
        if (Files.exists(outDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(outDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                if (!mConfig.isForced && BrutIO.isNonEmptyDirectory(outDir)) {
                    throw OutDirExistsException(outDir.path)
                }
                OS.rmdir(outDir)
            } else {
                if (!mConfig.isForced) {
                    throw OutDirExistsException(outDir.path)
                }
                OS.rmfile(outDir)
            }
        }
        OS.mkdir(outDir)

        if (mConfig.jobs > 1) {
            mWorker = BackgroundWorker(mConfig.jobs - 1)
        }
        try {
            val info = ApkInfo()
            apkInfo = info
            info.version = mConfig.version
            info.apkFile = mApkFile
            val smaliDecoder = SmaliDecoder(mApkFile, mConfig.isBaksmaliDebugMode)
            mSmaliDecoder = smaliDecoder
            val resDecoder = ResDecoder(info, mConfig)
            mResDecoder = resDecoder

            Log.i(
                TAG, "Using Apktool " + mConfig.version + " on " + mApkFile.name +
                    (if (mWorker != null) " with " + mConfig.jobs + " threads" else "")
            )

            decodeSources(outDir)
            decodeResources(outDir)
            decodeManifest(outDir)

            mWorker?.let { worker ->
                worker.waitForFinish()
                mFirstError.get()?.let { throw it }
            }

            copyOriginalFiles(outDir)
            copyRawFiles(outDir)
            copyUnknownFiles(outDir)
            writeApkInfo(outDir)
        } finally {
            mWorker?.shutdownNow()
            try {
                mApkFile.close()
            } catch (ignored: DirectoryException) {
            }
        }
    }

    /** 反汇编或原样拷贝 dex。 */
    @Throws(AndrolibException::class)
    private fun decodeSources(outDir: File) {
        if (!apkInfo!!.hasSources()) {
            return
        }

        try {
            val `in` = mApkFile.getDirectory()
            val allSrc = mConfig.isDecodeSourcesFull
            val noSrc = mConfig.isDecodeSourcesNone

            for (fileName in `in`.getFiles(allSrc)) {
                if (if (allSrc) !fileName.endsWith(".dex")
                    else !ApkInfo.CLASSES_FILES_PATTERN.matcher(fileName).matches()
                ) {
                    continue
                }

                if (noSrc) {
                    copySourcesRaw(outDir, fileName)
                } else {
                    decodeSourcesSmali(outDir, fileName)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun copySourcesRaw(outDir: File, fileName: String) {
        Log.i(TAG, "Copying raw $fileName...")
        try {
            val `in` = mApkFile.getDirectory()
            `in`.copyToDir(outDir, fileName)
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 有并发 worker 时提交异步任务，首个异常被记录后跳过后续任务。 */
    @Throws(AndrolibException::class)
    private fun decodeSourcesSmali(outDir: File, fileName: String) {
        val worker = mWorker
        if (worker != null) {
            worker.submit {
                if (mFirstError.get() == null) {
                    try {
                        decodeSourcesSmaliJob(outDir, fileName)
                    } catch (ex: AndrolibException) {
                        mFirstError.compareAndSet(null, ex)
                    }
                }
            }
        } else {
            decodeSourcesSmaliJob(outDir, fileName)
        }
    }

    @Throws(AndrolibException::class)
    private fun decodeSourcesSmaliJob(outDir: File, fileName: String) {
        Log.i(TAG, "Baksmaling $fileName...")
        mSmaliDecoder!!.decode(fileName, outDir)
    }

    @Throws(AndrolibException::class)
    private fun decodeResources(outDir: File) {
        if (!apkInfo!!.hasResources()) {
            return
        }

        if (mConfig.isDecodeResourcesFull) {
            mResDecoder!!.decodeResources(outDir)
        } else {
            copyResourcesRaw(outDir)
        }
    }

    @Throws(AndrolibException::class)
    private fun copyResourcesRaw(outDir: File) {
        Log.i(TAG, "Copying raw resources.arsc...")
        try {
            val `in` = mApkFile.getDirectory()
            `in`.copyToDir(outDir, "resources.arsc")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    @Throws(AndrolibException::class)
    private fun decodeManifest(outDir: File) {
        if (!apkInfo!!.hasManifest()) {
            return
        }

        if (!mConfig.isDecodeResourcesNone) {
            mResDecoder!!.decodeManifest(outDir)
        } else {
            copyManifestRaw(outDir)
        }
    }

    @Throws(AndrolibException::class)
    private fun copyManifestRaw(outDir: File) {
        Log.i(TAG, "Copying raw AndroidManifest.xml...")
        try {
            val `in` = mApkFile.getDirectory()
            `in`.copyToDir(outDir, "AndroidManifest.xml")
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 拷贝 assets/lib 等目录里不属于任何已知类别的文件。 */
    @Throws(AndrolibException::class)
    private fun copyRawFiles(outDir: File) {
        try {
            val `in` = mApkFile.getDirectory()
            val dexFiles: Set<String> = mSmaliDecoder!!.dexFiles
            val resFileMap = mResDecoder!!.resFileMap
            val noAssets = mConfig.isDecodeAssetsNone

            for (dirName in ApkInfo.RAW_DIRS) {
                if (!`in`.containsDir(dirName) || (noAssets && dirName == "assets")) {
                    continue
                }

                Log.i(TAG, "Copying $dirName...")
                for (subName in `in`.getDir(dirName).getFiles(true)) {
                    val fileName = dirName + `in`.separator + subName
                    if (!ApkInfo.ORIGINAL_FILES_PATTERN.matcher(fileName).matches() &&
                        !dexFiles.contains(fileName) && !resFileMap.containsKey(fileName)
                    ) {
                        `in`.copyToDir(outDir, fileName)
                    }
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 拷贝签名相关原始文件到 original/。 */
    @Throws(AndrolibException::class)
    private fun copyOriginalFiles(outDir: File) {
        val originalDir = File(outDir, "original")

        Log.i(TAG, "Copying original files...")
        try {
            val `in` = mApkFile.getDirectory()

            for (fileName in `in`.getFiles(true)) {
                if (ApkInfo.ORIGINAL_FILES_PATTERN.matcher(fileName).matches()) {
                    `in`.copyToDir(originalDir, fileName)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 不匹配标准资源模式的其余文件进 unknown/。 */
    @Throws(AndrolibException::class)
    private fun copyUnknownFiles(outDir: File) {
        val unknownDir = File(outDir, "unknown")

        Log.i(TAG, "Copying unknown files...")
        try {
            val `in` = mApkFile.getDirectory()
            val dexFiles: Set<String> = mSmaliDecoder!!.dexFiles
            val resFileMap = mResDecoder!!.resFileMap

            for (fileName in `in`.getFiles(true)) {
                if (!ApkInfo.STANDARD_FILES_PATTERN.matcher(fileName).matches() &&
                    !dexFiles.contains(fileName) && !resFileMap.containsKey(fileName)
                ) {
                    `in`.copyToDir(unknownDir, fileName)
                }
            }
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        }
    }

    /** 汇总 doNotCompress 并写出 apktool.yml。 */
    @Throws(AndrolibException::class)
    private fun writeApkInfo(outDir: File) {
        val info = apkInfo!!
        // 未解码 manifest 时，记录 dex 推断出的 opcode API 级别。
        if (!info.hasManifest() || mConfig.isDecodeResourcesNone) {
            val apiLevel = mSmaliDecoder!!.inferredApiLevel
            if (apiLevel > 0) {
                info.sdkInfo.minSdkVersion = apiLevel.toString()
            }
        }

        try {
            // 记录未压缩文件。
            val `in` = mApkFile.getDirectory()
            val resFileMap = mResDecoder!!.resFileMap
            val uncompressedExts = HashSet<String>()
            val uncompressedFiles = HashSet<String>()

            for (fileName in `in`.getFiles(true)) {
                if (`in`.getCompressionLevel(fileName) == 0) {
                    val ext = FilenameUtils.getExtension(fileName)
                    if (`in`.getSize(fileName) > 0 && ext.isNotEmpty() &&
                        NO_COMPRESS_EXT_PATTERN.matcher(ext).matches()
                    ) {
                        uncompressedExts.add(ext)
                    } else {
                        uncompressedFiles.add(resFileMap.getOrDefault(fileName, fileName))
                    }
                }
            }

            // 排除扩展名已被记录的文件。
            if (uncompressedExts.isNotEmpty() && uncompressedFiles.isNotEmpty()) {
                val it = uncompressedFiles.iterator()
                while (it.hasNext()) {
                    val fileName = it.next()
                    if (uncompressedExts.contains(FilenameUtils.getExtension(fileName))) {
                        it.remove()
                    }
                }
            }

            // 更新元数据。
            val doNotCompress = info.doNotCompress
            if (uncompressedExts.isNotEmpty()) {
                doNotCompress.addAll(uncompressedExts.toList().sorted())
            }
            if (uncompressedFiles.isNotEmpty()) {
                doNotCompress.addAll(uncompressedFiles.toList().sorted())
            }

            // 序列化 apktool.yml。
            info.save(File(outDir, "apktool.yml"))
        } catch (ex: DirectoryException) {
            throw AndrolibException(ex)
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
    }

    companion object {
        private val TAG = ApkDecoder::class.java.name

        /** aapt2 默认不压缩的扩展名白名单。 */
        private val NO_COMPRESS_EXT_PATTERN: Pattern = Pattern.compile(
            "dex|arsc|so|jpg|jpeg|png|gif|wav|mp2|mp3|ogg|aac|mpg|mpeg|mid|midi|smf|jet|" +
                "rtttl|imy|xmf|mp4|m4a|m4v|3gp|3gpp|3g2|3gpp2|amr|awb|wma|wmv|webm|webp|mkv"
        )
    }
}
