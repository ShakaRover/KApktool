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

import brut.common.Log
import brut.util.BrutIO
import brut.util.OS
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * 虚拟目录抽象：统一"文件系统目录"（[FileDirectory]）与"ZIP 内部目录"（[ZipRODirectory]）两种视图。
 *
 * 文件/子目录集合惰性加载（[load]），路径以 '/' 分隔；
 * 单层拆分见 [parsePath]，跨层操作通过递归委派完成。
 */
abstract class Directory : AutoCloseable {
    companion object {
        private const val TAG = ""
    }

    /** 路径分隔符（字符串形式）。 */
    @JvmField
    val separator: String = "/"

    /** 路径分隔符（字符形式）。 */
    @JvmField
    val separatorChar: Char = '/'

    /** 本层直接包含的文件名集合。 */
    protected var mFiles: MutableSet<String>? = null

    /** 递归文件路径缓存。 */
    protected var mFilesRecursive: MutableSet<String>? = null

    /** 本层直接包含的子目录映射。 */
    protected var mDirs: MutableMap<String, Directory>? = null

    /** 首次访问时填充 [mFiles] 与 [mDirs]。 */
    protected abstract fun load()

    @Throws(DirectoryException::class)
    protected open fun getFileInputImpl(name: String): InputStream =
        throw UnsupportedOperationException()

    @Throws(DirectoryException::class)
    protected open fun getFileOutputImpl(name: String): OutputStream =
        throw UnsupportedOperationException()

    protected open fun removeFileImpl(name: String) {
        throw UnsupportedOperationException()
    }

    @Throws(DirectoryException::class)
    protected open fun createDirImpl(name: String): Directory =
        throw UnsupportedOperationException()

    @Throws(DirectoryException::class)
    open fun getSize(name: String): Long = throw UnsupportedOperationException()

    @Throws(DirectoryException::class)
    open fun getCompressedSize(name: String): Long = throw UnsupportedOperationException()

    @Throws(DirectoryException::class)
    open fun getCompressionLevel(name: String): Int = throw UnsupportedOperationException()

    @Throws(DirectoryException::class)
    override fun close() {
        // 默认空实现。
    }

    /** 本层文件名集合。 */
    open fun getFiles(): MutableSet<String> = getFiles(false)

    /** [recursive] 为真时返回全部后代文件路径（带目录前缀），并缓存。 */
    @JvmOverloads
    open fun getFiles(recursive: Boolean): MutableSet<String> {
        var files = mFiles
        if (files == null) {
            load()
            files = mFiles!!
        }
        if (!recursive) {
            return files
        }

        var recursiveFiles = mFilesRecursive
        if (recursiveFiles == null) {
            recursiveFiles = LinkedHashSet(files)
            for ((dirName, dir) in getDirs()) {
                for (path in dir.getFiles(true)) {
                    recursiveFiles.add(dirName + separator + path)
                }
            }
            mFilesRecursive = recursiveFiles
        }
        return recursiveFiles
    }

    /** 本层子目录映射。 */
    open fun getDirs(): MutableMap<String, Directory> = getDirs(false)

    /** [recursive] 为真时返回全部后代目录（路径作键）。 */
    @JvmOverloads
    open fun getDirs(recursive: Boolean): MutableMap<String, Directory> {
        var dirs = mDirs
        if (dirs == null) {
            load()
            dirs = mDirs!!
        }
        if (!recursive) {
            return dirs
        }

        val result = LinkedHashMap(dirs)
        for ((dirName, dir) in dirs) {
            for ((subName, subDir) in dir.getDirs(true)) {
                result[dirName + separator + subName] = subDir
            }
        }
        return result
    }

    /** 判断路径指向的文件是否存在。 */
    fun containsFile(path: String): Boolean {
        val subpath = try {
            getSubPath(path)
        } catch (ignored: PathNotExist) {
            return false
        }

        val dir = subpath.dir
        if (dir != null) {
            return dir.containsFile(subpath.path)
        }
        return getFiles().contains(subpath.path)
    }

    /** 判断路径指向的子目录是否存在。 */
    fun containsDir(path: String): Boolean {
        val subpath = try {
            getSubPath(path)
        } catch (ignored: PathNotExist) {
            return false
        }

        val dir = subpath.dir
        if (dir != null) {
            return dir.containsDir(subpath.path)
        }
        return getDirs().containsKey(subpath.path)
    }

    /** 打开文件输入流。 */
    @Throws(DirectoryException::class)
    fun getFileInput(path: String): InputStream {
        val subpath = getSubPath(path)
        val dir = subpath.dir
        if (dir != null) {
            return dir.getFileInput(subpath.path)
        }

        if (!getFiles().contains(subpath.path)) {
            throw PathNotExist(path)
        }
        return getFileInputImpl(subpath.path)
    }

    /** 打开文件输出流；缺失的父目录自动创建。 */
    @Throws(DirectoryException::class)
    fun getFileOutput(path: String): OutputStream {
        val parsed = parsePath(path)
        val parsedDir = parsed.dir
        if (parsedDir == null) {
            getFiles().add(parsed.subpath)
            return getFileOutputImpl(parsed.subpath)
        }

        val dir = try {
            createDir(parsedDir)
        } catch (ignored: PathAlreadyExists) {
            getDirs()[parsedDir]
        }
        return dir!!.getFileOutput(parsed.subpath)
    }

    /** 删除文件；不存在时返回 false。 */
    fun removeFile(path: String): Boolean {
        val subpath = try {
            getSubPath(path)
        } catch (ignored: PathNotExist) {
            return false
        }

        val dir = subpath.dir
        if (dir != null) {
            return dir.removeFile(subpath.path)
        }
        val files = getFiles()
        if (!files.contains(subpath.path)) {
            return false
        }
        removeFileImpl(subpath.path)
        files.remove(subpath.path)
        return true
    }

    /** 获取已有子目录。 */
    @Throws(PathNotExist::class)
    fun getDir(path: String): Directory {
        val subpath = getSubPath(path)
        val dir = subpath.dir
        if (dir != null) {
            return dir.getDir(subpath.path)
        }
        val target = getDirs()[subpath.path] ?: throw PathNotExist(path)
        return target
    }

    /** 创建子目录；已存在时抛 [PathAlreadyExists]。 */
    @Throws(DirectoryException::class)
    fun createDir(path: String): Directory {
        val parsed = parsePath(path)
        val dirs = getDirs()
        val parsedDir = parsed.dir
        if (parsedDir == null) {
            if (dirs.containsKey(parsed.subpath)) {
                throw PathAlreadyExists(path)
            }
            val dir = createDirImpl(parsed.subpath)
            dirs[parsed.subpath] = dir
            return dir
        }

        val dir = if (dirs.containsKey(parsedDir)) {
            dirs[parsedDir]!!
        } else {
            val created = createDirImpl(parsedDir)
            dirs[parsedDir] = created
            created
        }
        return dir.createDir(parsed.subpath)
    }

    /** 把本目录全部文件拷入 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: Directory) {
        for (fileName in getFiles(true)) {
            copyToDir(out, fileName)
        }
    }

    /** 把单个文件按同名拷入 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: Directory, fileName: String) {
        copyToDir(fileName, out, fileName)
    }

    /** 把多个文件按同名拷入 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: Directory, vararg fileNames: String) {
        for (fileName in fileNames) {
            copyToDir(out, fileName)
        }
    }

    /** 把单个文件（或目录）以指定名字拷入另一 [Directory]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(inFileName: String, out: Directory, outFileName: String) {
        try {
            if (containsDir(inFileName)) {
                getDir(inFileName).copyToDir(out.createDir(outFileName))
            } else {
                BrutIO.copyAndClose(getFileInput(inFileName), out.getFileOutput(outFileName))
            }
        } catch (ex: IOException) {
            throw DirectoryException("Error copying file: $inFileName", ex)
        }
    }

    /** 把本目录全部文件拷入磁盘目录 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: File) {
        for (fileName in getFiles(true)) {
            copyToDir(out, fileName)
        }
    }

    /** 把单个文件按同名拷入磁盘目录 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: File, fileName: String) {
        copyToDir(fileName, out, fileName)
    }

    /** 把多个文件按同名拷入磁盘目录 [out]。 */
    @Throws(DirectoryException::class)
    fun copyToDir(out: File, vararg fileNames: String) {
        for (fileName in fileNames) {
            copyToDir(out, fileName)
        }
    }

    /**
     * 把单个文件（或目录）以指定名字拷入磁盘目录。
     *
     * 目标路径先经 [BrutIO.sanitizePath] 清洗，非法路径跳过并记 warning；
     * 目标已存在则先删除，不存在则先建父目录。
     */
    @Throws(DirectoryException::class)
    fun copyToDir(inFileName: String, out: File, outFileNameArg: String) {
        var outFileName = outFileNameArg
        try {
            if (containsDir(inFileName)) {
                getDir(inFileName).copyToDir(File(out, outFileName))
            } else if (containsFile(inFileName)) {
                outFileName = BrutIO.sanitizePath(out, outFileName)
                if (outFileName.isEmpty()) {
                    return
                }
                val outFile = File(out, outFileName)
                if (outFile.exists()) {
                    OS.rmfile(outFile)
                } else {
                    val parentDir = outFile.parentFile
                    if (parentDir != null) {
                        OS.mkdir(parentDir)
                    }
                }
                BrutIO.copyAndClose(getFileInput(inFileName), Files.newOutputStream(outFile.toPath()))
            } else {
                // 目录/文件不存在时静默返回。
                return
            }
        } catch (ex: InvalidPathException) {
            Log.w(TAG, "Skipping file %s (%s)", inFileName, ex.message)
        } catch (ex: IOException) {
            throw DirectoryException("Error copying file: $inFileName", ex)
        }
    }

    /** 把路径拆成"目录段 + 剩余"，目录段必须已存在。 */
    @Throws(PathNotExist::class)
    private fun getSubPath(path: String): SubPath {
        val parsed = parsePath(path)
        val parsedDir = parsed.dir ?: return SubPath(null, parsed.subpath)
        val dir = getDirs()[parsedDir] ?: throw PathNotExist(path)
        return SubPath(dir, parsed.subpath)
    }

    /** 只拆分第一个 '/' 之前的目录段。 */
    private fun parsePath(path: String): ParsedPath {
        val pos = path.indexOf(separatorChar)
        return if (pos == -1) {
            ParsedPath(null, path)
        } else {
            ParsedPath(path.substring(0, pos), path.substring(pos + 1))
        }
    }

    /** 单层路径拆分结果。 */
    private class ParsedPath(val dir: String?, val subpath: String)

    /** 已解析的子路径：所在目录（可为 null 表示本层）+ 剩余名。 */
    private class SubPath(val dir: Directory?, val path: String)
}
