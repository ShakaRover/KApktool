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
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom

/**
 * 打包资源（jar / 原生二进制等）提取工具。
 *
 * [getResourceAsFile] 把类路径资源释放为临时文件并缓存结果，
 * 同一资源在进程生命周期内只会解压一次（如 aapt2 可执行文件）。
 */
object Jar {
    private const val DEFAULT_TMP_PREFIX = "brut_util_Jar_"

    /** 资源名 -> 已解压临时文件 的进程内缓存。 */
    private val sExtracted = HashMap<String, File>()

    /** 以默认临时文件前缀把类路径资源提取为本地文件。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun getResourceAsFile(clz: Class<*>, name: String): File =
        getResourceAsFile(clz, name, DEFAULT_TMP_PREFIX)

    /** 把类路径资源提取为本地文件（同名资源命中缓存时直接复用）。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun getResourceAsFile(clz: Class<*>, name: String, tmpPrefix: String): File {
        var file = sExtracted[name]
        if (file == null) {
            file = extractToTmp(clz, name, tmpPrefix)
            sExtracted[name] = file
        }
        return file
    }

    /** 以默认临时文件前缀解压资源到系统临时目录。 */
    @JvmStatic
    @Throws(BrutException::class)
    fun extractToTmp(clz: Class<*>, name: String): File =
        extractToTmp(clz, name, DEFAULT_TMP_PREFIX)

    /**
     * 解压资源到系统临时目录。
     *
     * 随机数后缀避免并发/多实例下的临时文件名冲突；
     * Long.MIN_VALUE 取绝对值会溢出，故单独归零处理。
     */
    @JvmStatic
    @Throws(BrutException::class)
    fun extractToTmp(clz: Class<*>, name: String, tmpPrefix: String): File {
        try {
            val suffix0 = ThreadLocalRandom.current().nextLong()
            val suffix = if (suffix0 > Long.MIN_VALUE) Math.abs(suffix0) else 0
            val fileOut = File.createTempFile(tmpPrefix, "$suffix.tmp")
            fileOut.deleteOnExit()

            val inStream = clz.getResourceAsStream(name)
                ?: throw FileNotFoundException(name)

            inStream.use {
                BrutIO.copyAndClose(it, Files.newOutputStream(fileOut.toPath()))
            }
            return fileOut
        } catch (ex: IOException) {
            throw BrutException("Could not extract resource: $name", ex)
        }
    }
}
