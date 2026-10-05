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

import brut.xml.XmlUtils
import org.custommonkey.xmlunit.XMLAssert.assertXMLEqual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Document
import java.io.File

/**
 * 网络安全配置测试：工程本身没有任何网络安全配置时，
 * 开启 isNetSecConf 构建并解码，验证 apktool 自动生成了默认宽松配置并被 manifest 引用。
 */
class NoNetworkConfigTest : BaseTest() {

    /** 验证构建并解码后输出目录确实存在。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    /** 验证自动生成的默认配置文件同时包含 system 与 user 两个信任锚。 */
    @Test
    @Throws(Exception::class)
    fun netSecConfGeneric() {
        log("Comparing network security configuration file...")

        val expected =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<network-security-config>\n" +
            "    <base-config>\n" +
            "        <trust-anchors>\n" +
            "            <certificates src=\"system\"/>\n" +
            "            <certificates src=\"user\"/>\n" +
            "        </trust-anchors>\n" +
            "    </base-config>\n" +
            "</network-security-config>"

        val obtained = readTextFile(File(sTestNewDir!!, "res/xml/network_security_config.xml"))

        assertXMLEqual(expected, obtained)
    }

    /** 验证解码出的 manifest 中 application 正确引用了自动生成的配置文件。 */
    @Test
    @Throws(Exception::class)
    fun netSecConfInManifest() {
        log("Validating network security config in Manifest...")

        val doc = XmlUtils.loadDocument(File(sTestNewDir!!, "AndroidManifest.xml"), true)
        val expression = "/manifest/application/@android:networkSecurityConfig"
        val value = XmlUtils.evaluateXPath(doc, expression, String::class.java)
        assertEquals("@xml/network_security_config", value)
    }

    companion object {
        /** 类级初始化：解包无网络安全配置的对照组工程，开启 netSecConf 后构建再解码。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "network_config-orig")
            sTestNewDir = File(sTmpDir!!, "network_config-new")

            log("Unpacking network_config...")
            copyResourceDir(NoNetworkConfigTest::class.java, "network_config/none", sTestOrigDir!!)

            sConfig!!.isNetSecConf = true

            log("Building network_config.apk...")
            val testApk = File(sTmpDir!!, "network_config.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testApk)

            log("Decoding network_config.apk...")
            ApkDecoder(testApk, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
