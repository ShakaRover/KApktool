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
import java.io.OutputStream
import java.nio.file.Files
import java.util.Comparator
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * 基于真实文件系统目录的 [Directory] 实现。
 *
 * 构造时目录必须已存在；[load] 按名称排序扫描本层，忽略符号链接等非常规子项。
 */
class FileDirectory : Directory {
    private val mDir: File

    /** 以路径名打开目录。 */
    @Throws(DirectoryException::class)
    constructor(dirName: String) : this(File(dirName))

    /** 打开目录；[dir] 必须是已存在的目录。 */
    @Throws(DirectoryException::class)
    constructor(dir: File) : super() {
        if (!dir.isDirectory) {
            throw DirectoryException("file must be a directory: $dir")
        }
        mDir = dir
    }

    override fun load() {
        val files = LinkedHashSet<String>()
        val dirs = LinkedHashMap<String, Directory>()
        mFiles = files
        mDirs = dirs

        val listed = mDir.listFiles()
        if (listed != null) {
            java.util.Arrays.sort(listed, Comparator.comparing { f: File -> f.name })
            for (file in listed) {
                if (file.isFile) {
                    files.add(file.name)
                } else {
                    try {
                        dirs[file.name] = FileDirectory(file)
                    } catch (ignored: DirectoryException) {
                    }
                }
            }
        }
    }

    private fun generatePath(name: String): String = mDir.path + separator + name

    @Throws(DirectoryException::class)
    override fun getFileInputImpl(name: String): InputStream {
        return try {
            Files.newInputStream(File(generatePath(name)).toPath())
        } catch (ex: IOException) {
            throw DirectoryException(ex)
        }
    }

    @Throws(DirectoryException::class)
    override fun getFileOutputImpl(name: String): OutputStream {
        return try {
            Files.newOutputStream(File(generatePath(name)).toPath())
        } catch (ex: IOException) {
            throw DirectoryException(ex)
        }
    }

    override fun removeFileImpl(name: String) {
        brut.util.OS.rmfile(File(generatePath(name)))
    }

    @Throws(DirectoryException::class)
    override fun createDirImpl(name: String): Directory {
        val dir = File(generatePath(name))
        brut.util.OS.mkdir(dir)
        return FileDirectory(dir)
    }

    @Throws(DirectoryException::class)
    override fun getSize(name: String): Long {
        val file = File(generatePath(name))
        if (!file.isFile) {
            throw DirectoryException("file must be a file: $file")
        }
        return file.length()
    }

    @Throws(DirectoryException::class)
    override fun getCompressedSize(name: String): Long = getSize(name)

    override fun getCompressionLevel(name: String): Int = 0
}
