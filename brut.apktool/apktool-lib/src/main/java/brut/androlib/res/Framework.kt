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
package brut.androlib.res

import brut.androlib.Config
import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.FrameworkNotFoundException
import brut.androlib.meta.ApkInfo
import brut.androlib.res.decoder.BinaryResourceParser
import brut.androlib.res.table.ResTable
import brut.common.Log
import brut.util.BrutIO
import brut.util.OS
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * framework 资源包管理：安装/查询/清理 apktool framework 目录中的 apk。
 *
 * framework 文件命名 "包ID[-tag].apk"，缺 tag 时回退无 tag 版本；
 * android framework（ID=1）缺失时从内置 android-framework.jar 解出。
 * [install] 会把所有 entrySpec 标志位补上 SPEC_PUBLIC 以公开全部资源。
 */
class Framework(
    private val mConfig: Config,
) {
    private var mDirectory: File? = null

    /** 安装 APK 为 framework 包。 */
    @Throws(AndrolibException::class)
    fun install(apkFile: File) {
        try {
            ZipFile(apkFile).use { zip ->
                var entry = zip.getEntry("resources.arsc")
                    ?: throw AndrolibException("Could not find resources.arsc in file: $apkFile")

                val data = BrutIO.readAndClose(zip.getInputStream(entry))
                val table = parseAndPublicizeResources(data)
                val pkgId = table.listPackageGroups().iterator().next().getId()
                val outFile = File(getDirectory(), "$pkgId${getApkSuffix()}")

                ZipOutputStream(Files.newOutputStream(outFile.toPath())).use { out ->
                    out.setMethod(ZipOutputStream.STORED)
                    val crc = CRC32()
                    crc.update(data)
                    entry = ZipEntry("resources.arsc")
                    entry.size = data.size.toLong()
                    entry.method = ZipEntry.STORED
                    entry.crc = crc.value
                    out.putNextEntry(entry)
                    out.write(data)
                    out.closeEntry()

                    // 写入伪造的 AndroidManifest.xml 以兼容旧版 aapt。
                    val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    if (manifestEntry != null) {
                        val manifest = BrutIO.readAndClose(zip.getInputStream(manifestEntry))
                        val manifestCrc = CRC32()
                        manifestCrc.update(manifest)
                        manifestEntry.size = manifest.size.toLong()
                        manifestEntry.setCompressedSize(-1)
                        manifestEntry.crc = manifestCrc.value
                        out.putNextEntry(manifestEntry)
                        out.write(manifest)
                        out.closeEntry()
                    }
                }

                Log.i(TAG, "Framework installed to: $outFile")
            }
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
    }

    /** 解析资源表并把全部条目公开（SPEC_PUBLIC）。 */
    @Throws(AndrolibException::class)
    private fun parseAndPublicizeResources(data: ByteArray): ResTable {
        val table = ResTable(ApkInfo(), mConfig)
        val parser = BinaryResourceParser(table, true, true)
        parser.enableCollectFlagsOffsets()
        parser.parse(ByteArrayInputStream(data))

        if (table.getPackageGroupCount() == 0) {
            throw AndrolibException("No packages in resources.arsc in file.")
        }

        // 公开全部 entry spec。
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (pair in parser.getEntrySpecFlagsOffsets()!!) {
            var position = pair.first.toInt()
            val count: Int = pair.second
            for (i in 0 until count) {
                val flags = buffer.getInt(position)
                // ResTable_typeSpec::SPEC_PUBLIC
                buffer.putInt(position, flags or 0x40000000.toInt())
                position += 4
            }
        }

        return table
    }

    /** 获取（必要时创建）framework 目录。 */
    @Throws(AndrolibException::class)
    fun getDirectory(): File {
        var dir = mDirectory
        if (dir == null) {
            val path = mConfig.frameworkDirectory
            dir = if (!path.isNullOrEmpty()) File(path) else DEFAULT_DIRECTORY

            if (dir.exists() && !dir.isDirectory) {
                throw AndrolibException("Framework path is not a directory: $dir")
            }

            val parent = dir.parentFile
            if (parent != null && parent.exists() && !parent.isDirectory) {
                throw AndrolibException("Framework path's parent is not a directory: $parent")
            }

            if (!dir.exists() && !dir.mkdirs()) {
                throw AndrolibException("Could not create framework directory: $dir")
            }

            mDirectory = dir
        }

        return dir
    }

    /** 按包 ID 查找 framework apk（使用配置 tag）。 */
    @Throws(AndrolibException::class)
    fun getApkFile(id: Int): File = getApkFile(id, mConfig.frameworkTag)

    /** 按包 ID 与 tag 查找 framework apk；未命中逐级回退。 */
    @Throws(AndrolibException::class)
    fun getApkFile(id: Int, tag: String?): File {
        val dir = getDirectory()
        var apkFile = File(dir, "$id${getApkSuffix(tag)}")
        if (apkFile.exists()) {
            return apkFile
        }

        // 回退到无 tag 的 framework。
        apkFile = File(dir, "$id${getApkSuffix(null)}")
        if (apkFile.exists()) {
            return apkFile
        }

        // 请求默认 framework 但缺失时，解压内置版本。
        if (id == 1) {
            try {
                BrutIO.copyAndClose(androidFrameworkAsStream!!, Files.newOutputStream(apkFile.toPath()))
            } catch (ex: IOException) {
                throw AndrolibException(ex)
            }
            return apkFile
        }

        throw FrameworkNotFoundException(id)
    }

    private fun getApkSuffix(): String = getApkSuffix(mConfig.frameworkTag)

    /** 清空 framework 目录中全部 apk。 */
    @Throws(AndrolibException::class)
    fun cleanDirectory() {
        for (apkFile in listDirectory()) {
            Log.i(TAG, "Removing framework file: " + apkFile.name)
            OS.rmfile(apkFile)
        }
    }

    /** 列出 framework 目录中匹配的 apk 文件。 */
    @Throws(AndrolibException::class)
    fun listDirectory(): List<File> {
        val ignoreTag = mConfig.isForced
        val suffix = if (ignoreTag) getApkSuffix(null) else getApkSuffix()
        val apkFiles = ArrayList<File>()

        val files = getDirectory().listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile && isValidApkName(file.name, suffix, ignoreTag)) {
                    apkFiles.add(file)
                }
            }
        }

        return apkFiles
    }

    /** 把 arsc 文件的全部资源公开（原地改写）。 */
    @Throws(AndrolibException::class)
    fun publicizeResources(arscFile: File) {
        try {
            val data = Files.readAllBytes(arscFile.toPath())
            parseAndPublicizeResources(data)
            Files.write(arscFile.toPath(), data)
        } catch (ex: IOException) {
            throw AndrolibException(ex)
        }
    }

    companion object {
        private val TAG = Framework::class.java.name

        /** 平台默认 framework 目录（macOS/Windows/XDG 规则）。 */
        private val DEFAULT_DIRECTORY: File

        init {
            val userHome = System.getProperty("user.home")
            val defDir: java.nio.file.Path = if (brut.util.OSDetection.isMacOSX()) {
                Paths.get(userHome, "Library", "apktool", "framework")
            } else if (brut.util.OSDetection.isWindows()) {
                Paths.get(userHome, "AppData", "Local", "apktool", "framework")
            } else {
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                if (xdgDataHome != null) {
                    Paths.get(xdgDataHome, "apktool", "framework")
                } else {
                    Paths.get(userHome, ".local", "share", "apktool", "framework")
                }
            }
            DEFAULT_DIRECTORY = defDir.toFile()
        }

        /** framework 文件名后缀（含 tag）。 */
        private fun getApkSuffix(tag: String?): String =
            (if (!tag.isNullOrEmpty()) "-$tag" else "") + ".apk"

        /** 校验 framework 文件名：数字包 ID + 后缀。 */
        private fun isValidApkName(fileName: String, suffix: String, ignoreTag: Boolean): Boolean {
            if (!fileName.endsWith(suffix)) {
                return false
            }
            if (ignoreTag) {
                return true
            }

            val len = fileName.length - suffix.length
            if (len == 0) {
                return false
            }
            for (i in 0 until len) {
                val ch = fileName[i]
                if (ch < '0' || ch > '9') {
                    return false
                }
            }
            return true
        }

        /** 内置 android framework 资源流。 */
        private val androidFrameworkAsStream: InputStream?
            get() = Framework::class.java.getResourceAsStream("/prebuilt/android-framework.jar")
    }
}
