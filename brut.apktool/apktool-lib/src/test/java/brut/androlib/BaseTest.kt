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

import brut.androlib.res.Framework
import brut.common.Log
import brut.directory.FileDirectory
import brut.util.OS
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.DetailedDiff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.ElementQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.custommonkey.xmlunit.XMLUnit
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files

/**
 * 所有集成测试的抽象基类。
 *
 * 负责统一的测试环境搭建（临时目录、framework 清理）、全局配置，
 * 以及解码结果断言（XML 对比、二进制目录对比、资源复制等）。
 * 各测试类继承后通过 companion 中的静态成员共享状态。
 */
abstract class BaseTest {

    /** 每个测试方法执行前重置全局配置，避免测试之间互相污染。 */
    @Before
    fun beforeEachTest() {
        sConfig = Config(TAG)
    }

    companion object {
        // 测试日志与配置的标签。
        private const val TAG = "TEST"

        init {
            // 开启 XXE 防护，并让 XMLUnit 忽略属性顺序与空白差异。
            XMLUnit.setEnableXXEProtection(true)
            XMLUnit.setIgnoreAttributeOrder(true)
            XMLUnit.setIgnoreWhitespace(true)
        }

        // 全局配置对象，测试期间可被各测试类修改。
        @JvmField var sConfig: Config? = null

        // 本次测试运行的临时根目录。
        @JvmField var sTmpDir: File? = null

        // 对照组（期望结果）目录，由各测试类自行赋值。
        @JvmField var sTestOrigDir: File? = null

        // 实验组（实际解码/构建结果）目录，由各测试类自行赋值。
        @JvmField var sTestNewDir: File? = null

        /** 删除 framework 目录中的 1.apk，保证测试从干净状态开始。 */
        private fun cleanFrameworkFile() {
            val apkFile = File(Framework(sConfig!!).getDirectory(), "1.apk")
            if (apkFile.isFile) {
                OS.rmfile(apkFile.absolutePath)
            }
        }

        /** 测试类级别初始化：创建配置、清理 framework、建立临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeEachClass() {
            sConfig = Config(TAG)
            cleanFrameworkFile()

            sTmpDir = OS.createTempDirectory()
        }

        /** 测试类级别清理：删除临时目录并复位所有静态状态。 */
        @AfterClass
        @JvmStatic
        @Throws(Exception::class)
        fun afterEachClass() {
            sTestOrigDir = null
            sTestNewDir = null

            OS.rmdir(sTmpDir!!)
            sTmpDir = null

            cleanFrameworkFile()
            sConfig = null
        }

        /** 输出普通级别测试日志。 */
        @JvmStatic
        fun log(message: String, vararg args: Any?) {
            Log.i(TAG, message, *args)
        }

        /**
         * 从 classpath 资源目录复制整个目录到 outDir。
         * 兼容 file: 协议（展开目录资源）与 jar: 协议（jar 包内资源）两种场景。
         */
        @JvmStatic
        @Throws(Exception::class)
        protected fun copyResourceDir(clz: Class<*>?, dirPath: String, outDir: File) {
            var owner: Class<*> = clz ?: Class::class.java
            val loader = owner.classLoader

            var dirURL: URL? = loader.getResource(dirPath)
            if (dirURL != null && dirURL.protocol == "file") {
                val jarPath = URLDecoder.decode(dirURL.file, "UTF-8")
                FileDirectory(jarPath).copyToDir(outDir)
                return
            }

            if (dirURL == null) {
                val className = owner.name.replace('.', '/') + ".class"
                dirURL = loader.getResource(className)
            }

            if (dirURL!!.protocol == "jar") {
                val path = dirURL.path
                val jarPath = URLDecoder.decode(path.substring(5, path.indexOf('!')), "UTF-8")
                FileDirectory(jarPath).copyToDir(outDir)
            }
        }

        /** 以 UTF-8 无关的默认编码读取整个文本文件内容。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun readTextFile(file: File): String {
            return String(Files.readAllBytes(file.toPath()))
        }

        /** 读取文件头部指定字节数的原始内容。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun readHeaderOfFile(file: File, size: Int): ByteArray {
            val buffer = ByteArray(size)

            Files.newInputStream(file.toPath()).use { `in`: InputStream ->
                if (`in`.read(buffer) != buffer.size) {
                    throw IOException("File size too small for buffer length: $size")
                }
            }

            return buffer
        }

        /** 移除字符串中的所有换行符（\n 与 \r）。 */
        @JvmStatic
        protected fun replaceNewlines(value: String): String {
            return value.replace("[\n\r]".toRegex(), "")
        }

        /** 对比默认对照/实验目录下的二进制文件夹（仅校验文件存在性）。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareBinaryFolder(path: String) {
            compareBinaryFolder(sTestOrigDir!!, sTestNewDir!!, path)
        }

        /**
         * 对比两个目录下的二进制文件夹：
         * 要求 controlDir 中 path 目录下每个文件在 testDir 同名目录下也存在。
         */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareBinaryFolder(controlDir: File, testDir: File, path: String) {
            val controlBase = File(controlDir, path)
            val testBase = File(testDir, path)

            var exists = true

            for (fileName in FileDirectory(controlBase).getFiles(true)) {
                val control = File(controlBase, fileName)
                val test = File(testBase, fileName)

                if (!control.isFile || !test.isFile) {
                    exists = false
                }
            }

            assertTrue(exists)
        }

        /** 对比默认目录下的 res/<path> values 文件（按 name 属性匹配元素）。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareValuesFiles(path: String) {
            compareValuesFiles(sTestOrigDir!!, sTestNewDir!!, path)
        }

        /** 对比指定目录下 res/<path> 的 values XML（按 name 属性匹配元素）。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareValuesFiles(controlDir: File, testDir: File, path: String) {
            compareXmlFiles(controlDir, testDir, "res/$path", ElementNameAndAttributeQualifier("name"))
        }

        /** 对比默认目录下指定路径的 XML 文件（严格相等）。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareXmlFiles(path: String) {
            compareXmlFiles(sTestOrigDir!!, sTestNewDir!!, path)
        }

        /** 对比指定目录下指定路径的 XML 文件（严格相等）。 */
        @JvmStatic
        @Throws(Exception::class)
        protected fun compareXmlFiles(controlDir: File, testDir: File, path: String) {
            compareXmlFiles(controlDir, testDir, path, null)
        }

        /**
         * XML 对比核心实现：
         * - 无 qualifier 时使用 assertXMLEqual 严格对比；
         * - 有 qualifier 时使用 DetailedDiff 按元素限定器忽略顺序差异。
         */
        private fun compareXmlFiles(
            controlDir: File, testDir: File, path: String, qualifier: ElementQualifier?,
        ) {
            val control = FileReader(File(controlDir, path))
            val test = FileReader(File(testDir, path))
            try {
                if (qualifier == null) {
                    XMLAssert.assertXMLEqual(control as Reader, test as Reader)
                    return
                }

                val diff = DetailedDiff(Diff(control, test))
                diff.overrideElementQualifier(qualifier)

                assertTrue(path + ": " + diff.allDifferences.toString(), diff.similar())
            } finally {
                control.close()
                test.close()
            }
        }
    }
}
