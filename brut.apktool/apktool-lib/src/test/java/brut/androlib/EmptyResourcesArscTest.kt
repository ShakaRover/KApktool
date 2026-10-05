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

import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import org.junit.Assert.assertTrue

/**
 * 回归测试（issue1730）：验证资源表为空（无资源条目）的 APK
 * 在解码并重新构建后流程不中断，输出目录仍然有效。
 */
class EmptyResourcesArscTest : BaseTest() {

    /** 验证解码目录与原始对照目录均存在，说明空资源表往返流程未失败。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
        assertTrue(sTestOrigDir!!.isDirectory)
    }

    companion object {
        /** 类级初始化：解包、解码并重新构建 issue1730.apk。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.sTestOrigDir = File(BaseTest.sTmpDir!!, "issue1730-orig")
            BaseTest.sTestNewDir = File(BaseTest.sTmpDir!!, "issue1730-new")

            BaseTest.log("Unpacking issue1730.apk...")
            BaseTest.copyResourceDir(
                EmptyResourcesArscTest::class.java, "issue1730", BaseTest.sTestOrigDir!!
            )

            BaseTest.log("Decoding issue1730.apk...")
            val testApk = File(BaseTest.sTestOrigDir!!, "issue1730.apk")
            ApkDecoder(testApk, BaseTest.sConfig!!).decode(BaseTest.sTestNewDir!!)

            BaseTest.log("Building issue1730.apk...")
            ApkBuilder(BaseTest.sTestNewDir!!, BaseTest.sConfig!!).build(testApk)
        }
    }
}
