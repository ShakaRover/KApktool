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
 * CVE-2022-0476 恶意 YAML 回归测试。
 *
 * 构造含 SnakeYAML 反序列化 payload（!! 标签）的 apktool.yml，
 * 验证自研解析器只按纯文本读取字段、不会实例化任意类。
 */
class MaliciousYamlTest : BaseTest() {

    /** 加载恶意样本，断言 version 字段被安全地当作普通字符串读出。 */
    @Test
    @Throws(Exception::class)
    fun testMaliciousYaml() {
        val apkInfo = ApkInfo.load(this::class.java.getResourceAsStream("/meta/cve20220476.yml"))
        assertEquals("2.6.1-ddc4bb-SNAPSHOT", apkInfo.version)
    }
}
