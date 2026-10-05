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
import org.junit.Test

/**
 * apktool.yml 中含中文（圣书体/象形文字类非 ASCII）路径条目的读取测试。
 *
 * 验证 doNotCompress 列表里的中文字符串条目能被原样解析。
 */
class DoNotCompressHieroglyphTest : BaseTest() {

    /** 加载带中文资产路径的 YAML，断言版本、文件名与两条 doNotCompress 条目内容。 */
    @Test
    @Throws(Exception::class)
    fun testHieroglyph() {
        val apkInfo = ApkInfo.load(this::class.java.getResourceAsStream("/meta/donotcompress_with_hieroglyph.yml"))
        assertEquals("2.0.0", apkInfo.version)
        assertEquals("testapp.apk", apkInfo.apkFileName)
        assertEquals(2, apkInfo.doNotCompress.size)
        assertEquals("assets/AllAssetBundles/Andriod/tx_1001_冰原1", apkInfo.doNotCompress[0])
        assertEquals("assets/AllAssetBundles/Andriod/tx_1001_冰原1.manifest", apkInfo.doNotCompress[1])
    }
}
