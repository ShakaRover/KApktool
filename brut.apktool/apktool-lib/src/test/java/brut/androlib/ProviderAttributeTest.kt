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
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * issue636 回归测试：provider 组件属性（字符串资源引用）往返一致性。
 *
 * 验证解码并重建 APK 再次解码后，AndroidManifest.xml 中 provider
 * 的 android:label 等资源引用仍保持 @string/xxx 形式未被破坏。
 */
class ProviderAttributeTest : BaseTest() {

    /** 完整走一遍解码、重建、再解码流程，并对比 manifest 内容。 */
    @Test
    @Throws(Exception::class)
    fun isProviderStringReplacementWorking() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        ApkBuilder(testDir, sConfig!!).build(null)

        val newApk = File(testDir, "dist/" + testApk.name)
        val newDir = File("$testApk.out.new")
        ApkDecoder(newApk, sConfig!!).decode(newDir)

        val expected = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest package=\"com.ibotpeaches.issue636\" platformBuildVersionCode=\"22\" platformBuildVersionName=\"5.1-1756733\"\n" +
                "  xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application android:allowBackup=\"true\" android:debuggable=\"true\" android:icon=\"@mipmap/ic_launcher\" android:label=\"@string/app_name\" android:theme=\"@style/AppTheme\">\n" +
                "        <provider android:authorities=\"com.ibotpeaches.issue636.Provider\" android:exported=\"false\" android:grantUriPermissions=\"true\" android:label=\"@string/app_name\" android:multiprocess=\"false\" android:name=\"com.ibotpeaches.issue636.Provider\"/>\n" +
                "        <provider android:authorities=\"com.ibotpeaches.issue636.ProviderTwo\" android:exported=\"false\" android:grantUriPermissions=\"true\" android:label=\"@string/app_name\" android:multiprocess=\"false\" android:name=\"com.ibotpeaches.issue636.ProviderTwo\"/>\n" +
                "    </application>\n" +
                "</manifest>"
            )

        val obtained = readTextFile(File(newDir, "AndroidManifest.xml"))

        assertXMLEqual(expected, obtained)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue636.apk"

        /** 类级初始化：把 issue636 测试资源复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(ProviderAttributeTest::class.java, "issue636", sTmpDir!!)
        }
    }
}
