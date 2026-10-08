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
package brut.util

import brut.common.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.util.function.Predicate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 打包辅助：把目录树或单文件写入 [ZipOutputStream]。
 *
 * 命中"不压缩"名单的条目使用 STORED 方式并预先计算 CRC32；
 * 条目名统一转换为 Unix 分隔符，非法路径会被跳过并记录 warning。
 */
object ZipUtils {
    private const val TAG = ""

    /** 递归压缩整个目录。 */
    @JvmStatic
    @Throws(IOException::class)
    fun zipDir(dir: File, out: ZipOutputStream, doNotCompress: Collection<String>?) {
        zipDir(dir, null, out, doNotCompress)
    }

    /**
     * 递归压缩目录 [dirName]（相对 [baseDir]），条目名以 baseDir 为根。
     *
     * doNotCompress 同时支持完整条目名与扩展名两种匹配方式。
     */
    @JvmStatic
    @Throws(IOException::class)
    fun zipDir(
        baseDir: File,
        dirName: String?,
        out: ZipOutputStream,
        doNotCompress: Collection<String>?,
    ) {
        val dir = if (!dirName.isNullOrEmpty()) File(baseDir, dirName) else baseDir
        if (!dir.isDirectory) {
            return
        }

        val files = dir.listFiles() ?: return
        for (file in files) {
            val fileName = baseDir.toPath().relativize(file.toPath()).toString()

            if (file.isDirectory) {
                zipDir(baseDir, fileName, out, doNotCompress)
            } else if (file.isFile) {
                zipFile(
                    baseDir, fileName, out,
                    if (doNotCompress != null && doNotCompress.isNotEmpty()) {
                        Predicate { entryName: String ->
                            doNotCompress.contains(entryName) ||
                                doNotCompress.contains(BrutIO.getExtension(entryName))
                        }
                    } else {
                        Predicate { false }
                    }
                )
            }
        }
    }

    /** 压缩单个文件；[doNotCompress] 为 true 时以 STORED 方式写入。 */
    @JvmStatic
    @Throws(IOException::class)
    fun zipFile(baseDir: File, fileName: String, out: ZipOutputStream, doNotCompress: Boolean) {
        zipFile(baseDir, fileName, out, Predicate { doNotCompress })
    }

    private fun zipFile(
        baseDir: File,
        fileName: String,
        out: ZipOutputStream,
        doNotCompress: Predicate<String>,
    ) {
        var safeName = fileName
        try {
            safeName = BrutIO.sanitizePath(baseDir, safeName)
            if (safeName.isEmpty()) {
                return
            }

            val file = File(baseDir, safeName)
            if (!file.isFile) {
                return
            }

            val entryName = safeName.replace('\\', '/')
            val zipEntry = ZipEntry(entryName)

            if (doNotCompress.test(entryName)) {
                zipEntry.method = ZipEntry.STORED
                zipEntry.size = file.length()
                Files.newInputStream(file.toPath()).use { `in` ->
                    val crc = BrutIO.calculateCrc(`in`)
                    zipEntry.crc = crc.value
                }
            } else {
                zipEntry.method = ZipEntry.DEFLATED
            }

            out.putNextEntry(zipEntry)
            Files.newInputStream(file.toPath()).use { `in` ->
                `in`.copyTo(out)
            }
            out.closeEntry()
        } catch (ex: InvalidPathException) {
            Log.w(TAG, "Skipping file %s (%s)", safeName, ex.message)
        }
    }
}
