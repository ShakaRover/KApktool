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

import brut.common.BrutException
import brut.common.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 文件系统与外部进程操作的工具集合。
 *
 * 所有对外暴露的方法均通过 [JvmStatic] 提供静态入口，保持 Java 调用方写法不变；
 * 可能抛出受检异常 [BrutException] 的方法使用 `@Throws` 声明，
 * 以便残留的 Java 代码仍可编写对应的 catch 子句。
 */
object OS {
    private const val TAG = ""

    /** 按路径创建目录（含缺失的父级目录）。 */
    @JvmStatic
    fun mkdir(dir: String) = mkdir(File(dir))

    /**
     * 创建目录（含缺失的父级目录），已存在则直接返回。
     *
     * mkdirs() 对"目录已存在"与"创建失败（如磁盘/inode 耗尽）"都返回 false，
     * 因此需要复查 isDirectory 来区分两者，只把真实失败上报。
     */
    @JvmStatic
    fun mkdir(dir: File) {
        if (dir.isDirectory) {
            return
        }
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw RuntimeException("Failed to create directory: $dir")
        }
    }

    /** 删除文件（尽力而为，失败不抛异常）。 */
    @JvmStatic
    fun rmfile(file: String) = rmfile(File(file))

    /** 删除文件（尽力而为，失败不抛异常）。 */
    @JvmStatic
    fun rmfile(file: File) {
        file.delete()
    }

    /** 递归删除目录。 */
    @JvmStatic
    fun rmdir(dir: String) = rmdir(File(dir))

    /** 递归删除目录：先深度清理子项，再删除目录本身。 */
    @JvmStatic
    fun rmdir(dir: File) {
        if (!dir.isDirectory) {
            return
        }

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                rmdir(file)
            } else {
                rmfile(file)
            }
        }
        rmfile(dir)
    }

    /** 移动文件（目标存在时覆盖）。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun mvfile(src: String, dest: String) = mvfile(File(src), File(dest))

    /** 移动文件（目标存在时覆盖），失败时包装为 [BrutException]。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun mvfile(src: File, dest: File) {
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (ex: IOException) {
            throw BrutException("Could not move file: $src", ex)
        }
    }

    /** 复制文件（源不存在时静默跳过）。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun cpfile(src: String, dest: String) = cpfile(File(src), File(dest))

    /** 复制文件（目标存在时覆盖；源文件不存在时静默跳过）。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun cpfile(src: File, dest: File) {
        if (!src.isFile) {
            return
        }

        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (ex: IOException) {
            throw BrutException("Could not copy file: $src", ex)
        }
    }

    /** 递归复制目录。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun cpdir(src: String, dest: String) = cpdir(File(src), File(dest))

    /** 递归复制目录：先建目标目录，再逐项复制子文件与子目录。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun cpdir(src: File, dest: File) {
        if (!src.isDirectory) {
            return
        }

        mkdir(dest)

        val files = src.listFiles() ?: return

        for (file in files) {
            val destFile = File(dest, file.name)
            if (file.isDirectory) {
                cpdir(file, destFile)
            } else {
                cpfile(file, destFile)
            }
        }
    }

    /** 执行外部命令，等待其退出；非零退出码或 IO 异常抛出 [BrutException]。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun exec(cmd: Array<String>) {
        try {
            val builder = ProcessBuilder(*cmd)
            val ps = builder.start()

            StreamForwarder(ps.errorStream, "ERROR").start()
            StreamForwarder(ps.inputStream, "OUTPUT").start()

            val exitValue = ps.waitFor()
            if (exitValue != 0) {
                throw BrutException(
                    "Execution failed (exit code = $exitValue): " + Arrays.toString(cmd)
                )
            }
        } catch (ex: IOException) {
            throw BrutException("could not exec: " + Arrays.toString(cmd), ex)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BrutException("could not exec : " + Arrays.toString(cmd), ex)
        }
    }

    /**
     * 执行外部命令并返回其合并输出（stderr 并入 stdout）。
     *
     * 命令启动失败、超时或中断时返回 null，绝不抛异常。
     */
    @JvmStatic
    fun execAndReturn(cmd: Array<String>): String? {
        val executor: ExecutorService = Executors.newCachedThreadPool()
        return try {
            val builder = ProcessBuilder(*cmd)
            builder.redirectErrorStream(true)

            val process = builder.start()
            val collector = StreamCollector(process.inputStream)
            executor.execute(collector)
            process.waitFor(15, TimeUnit.SECONDS)
            executor.shutdownNow()

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Stream collector did not terminate.")
            }
            collector.get()
        } catch (ignored: IOException) {
            null
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /**
     * 创建独立的临时目录（带 BRUT 前缀，JVM 退出时自动删除）。
     *
     * 借助 createTempFile 抢占唯一名称，再删除同名文件并改建为目录。
     */
    @JvmStatic
    @Throws(BrutException::class)
    fun createTempDirectory(): File {
        try {
            val tmp = File.createTempFile("BRUT", null)
            tmp.deleteOnExit()

            if (!tmp.delete()) {
                throw BrutException("Could not delete tmp file: " + tmp.absolutePath)
            }
            if (!tmp.mkdir()) {
                throw BrutException("Could not create tmp dir: " + tmp.absolutePath)
            }

            return tmp
        } catch (ex: IOException) {
            throw BrutException("Could not create tmp dir", ex)
        }
    }

    /** 把子进程输出逐行转发到日志：OUTPUT 走 info，ERROR 走 warning。 */
    private class StreamForwarder(
        private val mIn: InputStream,
        private val mType: String,
    ) : Thread() {
        override fun run() {
            try {
                BufferedReader(InputStreamReader(mIn)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (mType == "OUTPUT") {
                            Log.i(TAG, line)
                        } else {
                            Log.w(TAG, line)
                        }
                    }
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    /** 把子进程输出整体收集为字符串。 */
    private class StreamCollector(private val mIn: InputStream) : Runnable {
        private val mBuffer = StringBuilder()

        override fun run() {
            try {
                BufferedReader(InputStreamReader(mIn)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        mBuffer.append(line).append('\n')
                    }
                }
            } catch (ignored: IOException) {
            }
        }

        fun get(): String = mBuffer.toString()
    }
}
