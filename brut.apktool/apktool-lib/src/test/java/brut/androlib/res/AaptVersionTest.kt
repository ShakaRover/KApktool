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
package brut.androlib.res

import brut.androlib.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * aapt 版本识别测试。
 *
 * 验证从 aapt version 输出文本中能正确判别大版本（aapt1 与 aapt2），
 * 覆盖新版带冒号/点号以及旧版 v0.x 两种格式。
 */
class AaptVersionTest : BaseTest() {

    /** 断言各种 aapt 版本字符串都能被解析为预期的主版本号。 */
    @Test
    @Throws(Exception::class)
    fun testAapt2Iterations() {
        assertEquals(2, AaptManager.getVersionFromString("Android Asset Packaging Tool (aapt) 2:17"))
        assertEquals(2, AaptManager.getVersionFromString("Android Asset Packaging Tool (aapt) 2.17"))
        assertEquals(1, AaptManager.getVersionFromString("Android Asset Packaging Tool, v0.9"))
        assertEquals(1, AaptManager.getVersionFromString("Android Asset Packaging Tool, v0.2-2679779"))
    }
}
