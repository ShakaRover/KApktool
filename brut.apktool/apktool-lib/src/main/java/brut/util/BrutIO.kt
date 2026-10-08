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

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.zip.CRC32

/**
 * IO 辅助工具：流读写（带关闭语义）、目录判断、递归时间戳、CRC 计算与路径清洗。
 *
 * [sanitizePath] 是防目录穿越（zip-slip / path traversal）的安全入口。
 */
object BrutIO {
    /** 读完整个输入流并关闭它。 */
    @JvmStatic
    @Throws(IOException::class)
    fun readAndClose(`in`: InputStream): ByteArray {
        try {
            return `in`.readBytes()
        } finally {
            closeQuietly(`in`)
        }
    }

    /** 把输入流拷贝进输出流，随后关闭两个流。 */
    @JvmStatic
    @Throws(IOException::class)
    fun copyAndClose(`in`: InputStream, out: OutputStream) {
        try {
            `in`.copyTo(out)
        } finally {
            closeQuietly(`in`)
            closeQuietly(out)
        }
    }

    /**
     * 取文件名的扩展名（最后一个 '.' 之后的部分），无扩展名时返回空串。
     *
     * 语义与 commons-io 的 `FilenameUtils.getExtension` 一致：最后一个路径分隔符
     * 之后的点才算扩展名分隔符，末尾的点返回空串。
     */
    @JvmStatic
    fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        val lastSeparator = maxOf(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'))
        return if (lastDot <= lastSeparator) "" else fileName.substring(lastDot + 1)
    }

    private fun closeQuietly(stream: java.io.Closeable?) {
        try {
            stream?.close()
        } catch (ignored: IOException) {
        }
    }

    /** 目录存在且至少包含一个条目时返回 true；无法读取目录时按"非空"处理（保守拒绝）。 */
    @JvmStatic
    fun isNonEmptyDirectory(file: File): Boolean {
        if (!file.isDirectory) {
            return false
        }
        return try {
            Files.newDirectoryStream(file.toPath()).use { stream -> stream.iterator().hasNext() }
        } catch (ignored: IOException) {
            true
        }
    }

    /** 取一组文件（递归）最后修改时间中的最大值。 */
    @JvmStatic
    fun recursiveModifiedTime(files: Array<File>): Long {
        var modified = 0L
        for (file in files) {
            val submodified = recursiveModifiedTime(file)
            if (submodified > modified) {
                modified = submodified
            }
        }
        return modified
    }

    /** 取单个文件（目录则递归）的最后修改时间。 */
    @JvmStatic
    fun recursiveModifiedTime(file: File): Long {
        var modified = file.lastModified()
        if (file.isDirectory) {
            val subfiles = file.listFiles()
            if (subfiles != null) {
                for (subfile in subfiles) {
                    val submodified = recursiveModifiedTime(subfile)
                    if (submodified > modified) {
                        modified = submodified
                    }
                }
            }
        }
        return modified
    }

    /** 流式计算输入数据的 CRC32（不关闭输入流）。 */
    @JvmStatic
    @Throws(IOException::class)
    fun calculateCrc(`in`: InputStream): CRC32 {
        val crc = CRC32()
        val buffer = ByteArray(8192)
        while (true) {
            val bytesRead = `in`.read(buffer)
            if (bytesRead == -1) break
            crc.update(buffer, 0, bytesRead)
        }
        return crc
    }

    /**
     * 把外部传入的相对路径规范化为 baseDir 内的安全相对路径。
     *
     * 拒绝空路径、绝对路径以及 normalize 后逃逸出 baseDir 的路径（目录穿越），
     * 抛出 [InvalidPathException]。
     */
    @JvmStatic
    @Throws(IOException::class)
    fun sanitizePath(baseDir: File, path: String?): String {
        if (path.isNullOrEmpty()) {
            throw InvalidPathException(path, "Path is null or empty")
        }

        val origPath = Paths.get(path)
        if (origPath.isAbsolute) {
            throw InvalidPathException(path, "Absolute paths are not allowed")
        }

        val basePath = Paths.get(baseDir.canonicalPath)
        val resolvedPath = basePath.resolve(origPath).normalize()
        if (!resolvedPath.startsWith(basePath)) {
            throw InvalidPathException(path, "Path traverses outside the base directory")
        }

        return basePath.relativize(resolvedPath).toString()
    }
}
