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
package brut.androlib.meta

import brut.androlib.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * apktool.yml 序列化（保存后重新加载）往返测试。
 *
 * 重点验证特殊字符条目：代理对 emoji、单/双引号、前后空格、
 * 字面反斜杠与制表符等在 YAML 写出再读回后保持原值。
 */
class ApkInfoSerializationTest : BaseTest() {

    /** 从资源加载对照样本断言一次，save 后重新加载再断言一次，验证往返一致。 */
    @Test
    @Throws(Exception::class)
    fun checkApkInfoSerialization() {
        val control = ApkInfo.load(this::class.java.getResourceAsStream("/meta/serialization.yml"))
        check(control)
        val testFile = File(sTmpDir!!, "serialization.yml")
        control.save(testFile)
        val test = ApkInfo.load(testFile)
        check(test)
    }

    /** 对 ApkInfo 各字段断言：版本、文件名、框架、资源、版本信息与 12 条 doNotCompress 特殊值。 */
    private fun check(apkInfo: ApkInfo) {
        assertEquals("2.0.0", apkInfo.version)
        assertEquals("testapp.apk", apkInfo.apkFileName)
        assertNotNull(apkInfo.usesFramework)
        assertEquals(1, apkInfo.usesFramework.ids.size)
        assertEquals(1, apkInfo.usesFramework.ids[0])
        assertNotNull(apkInfo.resourcesInfo)
        assertEquals(127, apkInfo.resourcesInfo.getPackageId())
        assertNotNull(apkInfo.versionInfo)
        assertEquals(1, apkInfo.versionInfo.getVersionCode())
        assertEquals("1.0", apkInfo.versionInfo.versionName)
        assertNotNull(apkInfo.doNotCompress)
        assertEquals(12, apkInfo.doNotCompress.size)
        assertEquals("assets/0byte_file.jpg", apkInfo.doNotCompress[0])
        assertEquals("arsc", apkInfo.doNotCompress[1])
        assertEquals("png", apkInfo.doNotCompress[2])
        assertEquals("mp3", apkInfo.doNotCompress[3])
        assertEquals("stored.file", apkInfo.doNotCompress[4])
        assertEquals("surrogate/pair/😊.test", apkInfo.doNotCompress[5])
        assertEquals("'single-quoted", apkInfo.doNotCompress[6])
        assertEquals("\"single-quoted", apkInfo.doNotCompress[7])
        assertEquals(" single-quoted", apkInfo.doNotCompress[8])
        assertEquals("single-quoted ", apkInfo.doNotCompress[9])
        assertEquals("\\\"single-quoted-with-\\tab", apkInfo.doNotCompress[10])
        assertEquals("\"double-quoted-with-\tab", apkInfo.doNotCompress[11])
    }
}
