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
import org.custommonkey.xmlunit.XMLAssert.assertXMLEqual
import java.io.File

/**
 * doctype（外部实体 / DOCTYPE 声明）安全解析测试。
 *
 * 验证带 doctype 的 APK 在解码后 AndroidManifest.xml 内容正确，
 * 且解析器不会因外部实体声明而出错或被利用。
 */
class ExternalEntityTest : BaseTest() {

    /** 对比解码产出的 AndroidManifest.xml 与期望文本是否等价。 */
    @Test
    @Throws(Exception::class)
    fun doctypeTest() {
        val expected = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest hardwareAccelerated=\"true\" package=\"com.ibotpeaches.doctype\" platformBuildVersionCode=\"24\" platformBuildVersionName=\"6.0-2456767\"\n" +
                "  xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <supports-screens android:anyDensity=\"true\" android:smallScreens=\"true\" android:normalScreens=\"true\" android:largeScreens=\"true\" android:resizeable=\"true\" android:xlargeScreens=\"true\" />\n" +
                "</manifest>"
            )

        val obtained = readTextFile(File(sTestNewDir!!, "AndroidManifest.xml"))

        assertXMLEqual(expected, obtained)
    }

    companion object {
        /** 类级初始化：解包 doctype 资源，构建并解码 doctype.apk。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "doctype-orig")
            sTestNewDir = File(sTmpDir!!, "doctype-new")

            log("Unpacking doctype...")
            copyResourceDir(
                ExternalEntityTest::class.java, "doctype", sTestOrigDir!!
            )

            log("Building doctype.apk...")
            val testApk = File(sTmpDir!!, "doctype.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testApk)

            log("Decoding doctype.apk...")
            ApkDecoder(testApk, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
