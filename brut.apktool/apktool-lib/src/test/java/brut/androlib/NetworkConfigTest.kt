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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File

/**
 * 网络安全配置测试：工程已自带 network_security_config 资源时，
 * 开启 isNetSecConf 构建并解码，验证配置内容（system 与 user 证书）与 manifest 引用均正确。
 */
class NetworkConfigTest : BaseTest() {

    /** 验证构建并解码后输出目录确实存在。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    /** 验证解码出的网络安全配置同时包含 system 与 user 两个信任锚。 */
    @Test
    @Throws(Exception::class)
    fun netSecConfGeneric() {
        log("Verifying network security configuration file contains user and system certificates...")

        val doc = XmlUtils.loadDocument(File(sTestNewDir!!, "res/xml/network_security_config.xml"))

        // 检查是否存在 'system' 证书
        val systemCertExpr = "/network-security-config/base-config/trust-anchors/certificates[@src='system']"
        val systemCertNode = XmlUtils.evaluateXPath(doc, systemCertExpr, Node::class.java)
        assertNotNull(systemCertNode)

        // 检查是否存在 'user' 证书
        val userCertExpr = "/network-security-config/base-config/trust-anchors/certificates[@src='user']"
        val userCertNode = XmlUtils.evaluateXPath(doc, userCertExpr, Node::class.java)
        assertNotNull(userCertNode)
    }

    /** 验证解码出的 manifest 中 application 正确引用了该网络安全配置文件。 */
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
        /** 类级初始化：解包已有网络安全配置的对照组工程，开启 netSecConf 后构建再解码。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "network_config-orig")
            sTestNewDir = File(sTmpDir!!, "network_config-new")

            log("Unpacking network_config...")
            copyResourceDir(NetworkConfigTest::class.java, "network_config/existing", sTestOrigDir!!)

            sConfig!!.isNetSecConf = true

            log("Building network_config.apk...")
            val testApk = File(sTmpDir!!, "network_config.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(testApk)

            log("Decoding network_config.apk...")
            ApkDecoder(testApk, sConfig!!).decode(sTestNewDir!!)
        }
    }
}
