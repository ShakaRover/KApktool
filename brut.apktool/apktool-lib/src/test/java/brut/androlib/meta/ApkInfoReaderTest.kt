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
import brut.yaml.YamlSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * apktool.yml 读取（YAML 解析）测试。
 *
 * 覆盖缩进错误应抛出语法异常、标准字段解析、未知字段/未知文件列表的
 * 容忍解析，以及带缩进列表的完整字段读取。
 */
class ApkInfoReaderTest : BaseTest() {

    /** 验证首行缩进错误的 YAML 应抛出 YamlSyntaxException。 */
    @Test(expected = YamlSyntaxException::class)
    @Throws(Exception::class)
    fun testIncorrectIndentFirst() {
        ApkInfo.load(this::class.java.getResourceAsStream("/meta/incorrect_indent_first.yml"))
    }

    /** 验证中间行缩进错误的 YAML 应抛出 YamlSyntaxException。 */
    @Test(expected = YamlSyntaxException::class)
    @Throws(Exception::class)
    fun testIncorrectIndentMiddle() {
        ApkInfo.load(this::class.java.getResourceAsStream("/meta/incorrect_indent_middle.yml"))
    }

    /** 验证标准 apktool.yml 的全部字段解析结果。 */
    @Test
    @Throws(Exception::class)
    fun testStandard() {
        checkStandard(ApkInfo.load(this::class.java.getResourceAsStream("/meta/standard.yml")))
    }

    /** 验证含未知字段的 apktool.yml 能被容忍解析且已知字段结果不变。 */
    @Test
    @Throws(Exception::class)
    fun testUnknownFields() {
        checkStandard(ApkInfo.load(this::class.java.getResourceAsStream("/meta/unknown_fields.yml")))
    }

    /** 标准用例的共同断言：版本、文件名、doNotCompress、资源/SDK/框架/版本信息。 */
    private fun checkStandard(apkInfo: ApkInfo) {
        assertEquals("2.8.1", apkInfo.version)
        assertEquals("standard.apk", apkInfo.apkFileName)
        assertEquals(1, apkInfo.doNotCompress.size)
        assertEquals("arsc", apkInfo.doNotCompress[0])
        assertNotNull(apkInfo.resourcesInfo)
        assertEquals(127, apkInfo.resourcesInfo.getPackageId())
        assertNull(apkInfo.resourcesInfo.packageName)
        assertFalse(apkInfo.resourcesInfo.isSparseEntries())
        assertNotNull(apkInfo.sdkInfo)
        assertEquals("25", apkInfo.sdkInfo.minSdkVersion)
        assertEquals("30", apkInfo.sdkInfo.targetSdkVersion)
        assertNotNull(apkInfo.usesFramework)
        assertNotNull(apkInfo.usesFramework.ids)
        assertEquals(1, apkInfo.usesFramework.ids.size)
        assertEquals(1, apkInfo.usesFramework.ids[0])
        assertNull(apkInfo.usesFramework.tag)
        assertNotNull(apkInfo.versionInfo)
        assertEquals(-1, apkInfo.versionInfo.getVersionCode())
        assertNull(apkInfo.versionInfo.versionName)
    }

    /** 验证含 unknownFiles 列表的旧版 apktool.yml 解析。 */
    @Test
    @Throws(Exception::class)
    fun testUnknownFiles() {
        val apkInfo = ApkInfo.load(this::class.java.getResourceAsStream("/meta/unknown_files.yml"))
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
        assertEquals(5, apkInfo.doNotCompress.size)
        assertEquals("assets/0byte_file.jpg", apkInfo.doNotCompress[0])
        assertEquals("arsc", apkInfo.doNotCompress[1])
        assertEquals("png", apkInfo.doNotCompress[2])
        assertEquals("mp3", apkInfo.doNotCompress[3])
        assertEquals("stored.file", apkInfo.doNotCompress[4])
    }

    /** 验证带缩进写法的列表字段（tag、maxSdkVersion、doNotCompress 等）解析。 */
    @Test
    @Throws(Exception::class)
    fun testListWithIndent() {
        val apkInfo = ApkInfo.load(this::class.java.getResourceAsStream("/meta/list_with_indent.yml"))
        assertEquals("2.8.0", apkInfo.version)
        assertEquals("basic.apk", apkInfo.apkFileName)
        assertNotNull(apkInfo.usesFramework)
        assertEquals(1, apkInfo.usesFramework.ids.size)
        assertEquals(1, apkInfo.usesFramework.ids[0])
        assertEquals("tag", apkInfo.usesFramework.tag)
        assertNotNull(apkInfo.resourcesInfo)
        assertEquals(127, apkInfo.resourcesInfo.getPackageId())
        assertEquals("com.test.basic", apkInfo.resourcesInfo.packageName)
        assertTrue(apkInfo.resourcesInfo.isSparseEntries())
        assertNotNull(apkInfo.sdkInfo)
        assertEquals("4", apkInfo.sdkInfo.minSdkVersion)
        assertEquals("22", apkInfo.sdkInfo.targetSdkVersion)
        assertEquals("30", apkInfo.sdkInfo.maxSdkVersion)
        assertNotNull(apkInfo.versionInfo)
        assertEquals(71, apkInfo.versionInfo.getVersionCode())
        assertEquals("1.0.70", apkInfo.versionInfo.versionName)
        assertNotNull(apkInfo.doNotCompress)
        assertEquals(2, apkInfo.doNotCompress.size)
        assertEquals("arsc", apkInfo.doNotCompress[0])
        assertEquals("png", apkInfo.doNotCompress[1])
    }
}
