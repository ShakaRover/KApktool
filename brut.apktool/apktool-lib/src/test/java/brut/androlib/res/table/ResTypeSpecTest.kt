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
package brut.androlib.res.table

import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ResTypeSpec] 混淆类型名兜底与改名契约测试（`--infer-res-type-names` 的基础）。
 */
class ResTypeSpecTest {

    /** 合法类型名原样保留。 */
    @Test
    fun validNameIsKept() {
        val spec = newPackage().addTypeSpec(1, "anim")
        assertFalse(spec.needsNameInference)
        assertEquals("anim", spec.name)
    }

    /** 非法/混淆类型名先兜底为 invalid%02X，改名后变为合法名。 */
    @Test
    fun invalidNameBecomesPlaceholderThenRenames() {
        val spec = newPackage().addTypeSpec(1, "ulfnf0000")
        assertTrue(spec.needsNameInference)
        assertEquals("invalid01", spec.name)

        spec.rename("anim")
        assertFalse(spec.needsNameInference)
        assertEquals("anim", spec.name)
    }

    /** 改名只接受合法类型名。 */
    @Test(expected = IllegalArgumentException::class)
    fun renameRejectsInvalidName() {
        newPackage().addTypeSpec(1, "ulfnf0000").rename("not-a-type")
    }

    private fun newPackage(): ResPackage {
        val table = ResTable(ApkInfo(), Config("test"))
        return table.addPackageGroup(0x7f, "com.example").getBasePackage()
    }
}
