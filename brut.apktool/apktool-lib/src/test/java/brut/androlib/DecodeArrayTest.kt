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

import brut.androlib.meta.ApkInfo
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResTable
import brut.androlib.res.table.value.ResArray
import brut.androlib.res.table.value.ResValue
import brut.directory.ExtFile
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * 数组资源解码测试（issue1994）：加载 APK 资源表后，
 * 按资源 ID 解析数组与字符串数组条目，验证均被正确还原为 ResArray 类型。
 */
class DecodeArrayTest : BaseTest() {

    /** 验证资源 0x7F020001（string-array）解析结果为 ResArray。 */
    @Test
    @Throws(Exception::class)
    fun decodeStringArray() {
        val value: ResValue = sTable.resolveEntry(ResId.of(0x7F020001)).value!!
        assertTrue("Not a ResArray. Found: " + value.javaClass, value is ResArray)
    }

    /** 验证资源 0x7F020000（array）解析结果为 ResArray。 */
    @Test
    @Throws(Exception::class)
    fun decodeArray() {
        val value: ResValue = sTable.resolveEntry(ResId.of(0x7F020000)).value!!
        assertTrue("Not a ResArray. Found: " + value.javaClass, value is ResArray)
    }

    companion object {
        // 测试用 APK 文件句柄。
        private var sTestApk: ExtFile? = null

        // 已加载的测试资源表。
        private lateinit var sTable: ResTable

        /** 类级初始化：解包 issue1994 资源并加载其资源表。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(MissingVersionManifestTest::class.java, "issue1994", sTmpDir!!)

            log("Decoding issue1994.apk...")
            sTestApk = ExtFile(sTmpDir!!, "issue1994.apk")
            val testInfo = ApkInfo()
            testInfo.apkFile = sTestApk
            sTable = ResTable(testInfo, sConfig!!)
            sTable.load()
        }

        /** 类级清理：关闭 APK 文件句柄释放资源。 */
        @AfterClass
        @JvmStatic
        @Throws(Exception::class)
        fun afterClass() {
            sTestApk!!.close()
        }
    }
}
