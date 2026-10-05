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
import org.custommonkey.xmlunit.XMLAssert.assertXMLEqual

/**
 * 回归测试（issue1157）：验证资源表被极度压缩（minified ARSC）的 APK
 * 解码后，布局 XML 中的自定义属性与命名空间前缀仍能正确还原。
 */
class MinifiedArscTest : BaseTest() {

    /** 校验解码出的 res/xml/custom.xml 与期望的布局内容完全一致。 */
    @Test
    @Throws(Exception::class)
    fun checkIfMinifiedArscLayoutFileMatchesTest() {
        val expected = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout n1:orientation=\"vertical\" n1:layout_width=\"match_parent\" n1:layout_height=\"match_parent\" xmlns:n1=\"http://schemas.android.com/apk/res/android\">\n" +
            "    <com.ibotpeaches.issue1157.MyCustomView n1:max=\"100\" n2:default_value=\"1.0\" n2:max_value=\"5.0\" n2:min_value=\"0.2\" xmlns:n2=\"http://schemas.android.com/apk/res-auto\" />\n" +
            "</LinearLayout>"

        val obtained = readTextFile(File(sTestNewDir!!, "res/xml/custom.xml"))

        assertXMLEqual(expected, obtained)
    }

    companion object {
        /** 类级初始化：解包 issue1157 资源并解码精简资源表 APK。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(MinifiedArscTest::class.java, "issue1157", BaseTest.sTmpDir!!)

            val testApk = File(BaseTest.sTmpDir!!, "issue1157.apk")
            BaseTest.sTestNewDir = File("$testApk.out")

            ApkDecoder(testApk, BaseTest.sConfig!!).decode(BaseTest.sTestNewDir!!)
        }
    }
}
