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

import org.custommonkey.xmlunit.XMLAssert.assertXMLEqual
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试（issue2328）：原工程 manifest 中 debuggable 已为 true 时，
 * 设置 isDebuggable=true 重新构建并解码，验证该属性被原样保留。
 */
class DebuggableTrueRetainedTest : BaseTest() {

    /** 验证构建并解码后输出目录确实存在。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    /** 验证解码结果 AndroidManifest 仍然保持 debuggable=true。 */
    @Test
    @Throws(Exception::class)
    fun debugIsTruePriorToBeingFalseTest() {
        val expected =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest package=\"com.ibotpeaches.issue2328\" platformBuildVersionCode=\"20\" platformBuildVersionName=\"4.4W.2-1537038\"\n" +
            "  xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
            "    <application android:debuggable=\"true\"/>\n" +
            "</manifest>"

        val obtained = readTextFile(File(sTestNewDir!!, "AndroidManifest.xml"))

        assertXMLEqual(expected, obtained)
    }

    companion object {
        /** 类级初始化：解包已带 debuggable=true 的对照组工程，开启 debuggable 后构建再解码。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "issue2328-debuggable-true-orig")
            sTestNewDir = File(sTmpDir!!, "issue2328-debuggable-true-new")

            log("Unpacking issue2328-debuggable-true...")
            copyResourceDir(DebuggableTrueRetainedTest::class.java, "issue2328/debuggable-true", sTestOrigDir!!)

            sConfig!!.isDebuggable = true
            sConfig!!.isVerbose = true

            log("Building issue2328-debuggable-true.apk...")
            val testApk = File(sTmpDir!!, "issue2328-debuggable-true.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testApk)

            log("Decoding issue2328-debuggable-true.apk...")
            ApkDecoder(testApk, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
