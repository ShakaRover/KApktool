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
package brut.directory

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 以 ZIP/JAR 包内某一路径为根的只读 [Directory] 视图。
 *
 * [mPath] 为包内前缀（空串表示根）；子目录通过共享 [ZipFile] 的私有构造复用句柄。
 * 含 ".." 段的条目一律忽略，防止通过畸形 ZIP 逃逸出当前目录视图。
 */
class ZipRODirectory private constructor(
    private val mZipFile: ZipFile,
    private val mPath: String,
) : Directory() {
    /** 打开指定路径的压缩包文件。 */
    @Throws(DirectoryException::class)
    constructor(file: File, path: String) : this(openZip(file), path)

    /** 打开压缩包根目录。 */
    @Throws(DirectoryException::class)
    constructor(file: File) : this(file, "")

    /** 打开指定路径的压缩包文件（路径名）。 */
    @Throws(DirectoryException::class)
    constructor(fileName: String, path: String) : this(File(fileName), path)

    /** 打开压缩包根目录（路径名）。 */
    @Throws(DirectoryException::class)
    constructor(fileName: String) : this(File(fileName), "")

    override fun load() {
        val files = LinkedHashSet<String>()
        val dirs = LinkedHashMap<String, Directory>()
        mFiles = files
        mDirs = dirs

        val prefixLen = mPath.length
        val entries = mZipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name

            if (name == mPath || !name.startsWith(mPath) || name.contains("..$separator")) {
                continue
            }

            var subname = name.substring(prefixLen)

            val pos = subname.indexOf(separatorChar)
            if (pos == -1) {
                if (!entry.isDirectory) {
                    files.add(subname)
                    continue
                }
            } else {
                subname = subname.substring(0, pos)
            }

            if (!dirs.containsKey(subname)) {
                dirs[subname] = ZipRODirectory(mZipFile, mPath + subname + separator)
            }
        }
    }

    @Throws(DirectoryException::class)
    private fun getZipFileEntry(name: String): ZipEntry {
        val entry = mZipFile.getEntry(name)
            ?: throw PathNotExist("Entry not found: $name")
        return entry
    }

    @Throws(DirectoryException::class)
    override fun getFileInputImpl(name: String): InputStream {
        return try {
            mZipFile.getInputStream(ZipEntry(mPath + name))
        } catch (ex: IOException) {
            throw PathNotExist(name, ex)
        }
    }

    @Throws(DirectoryException::class)
    override fun getSize(name: String): Long = getZipFileEntry(name).size

    @Throws(DirectoryException::class)
    override fun getCompressedSize(name: String): Long = getZipFileEntry(name).compressedSize

    @Throws(DirectoryException::class)
    override fun getCompressionLevel(name: String): Int = getZipFileEntry(name).method

    @Throws(DirectoryException::class)
    override fun close() {
        try {
            mZipFile.close()
        } catch (ex: IOException) {
            throw DirectoryException(ex)
        }
    }

    companion object {
        @Throws(DirectoryException::class)
        private fun openZip(file: File): ZipFile = try {
            ZipFile(file)
        } catch (ex: IOException) {
            throw DirectoryException(ex)
        }
    }
}
