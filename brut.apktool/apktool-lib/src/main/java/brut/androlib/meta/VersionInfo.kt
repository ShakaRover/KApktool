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

import brut.yaml.YamlPullParser
import brut.yaml.YamlSerializable
import brut.yaml.YamlSerializer
import java.io.IOException

/**
 * apktool.yml 的 versionInfo 节：versionCode / versionName。
 *
 * 字段用可空包装类表达"未设置"；[getVersionCode] 未设置时返回 -1。
 */
class VersionInfo : YamlSerializable {
    private var mVersionCode: Int? = null
    private var mVersionName: String? = null

    /** 清空全部字段。 */
    fun clear() {
        mVersionCode = null
        mVersionName = null
    }

    /** 是否没有任何版本信息。 */
    val isEmpty: Boolean
        get() = mVersionCode == null && mVersionName == null

    @Throws(IOException::class)
    override fun onEntry(parser: YamlPullParser) {
        when (parser.getKey()) {
            "versionCode" -> mVersionCode = parser.getInt()
            "versionName" -> mVersionName = parser.getString()
        }
    }

    @Throws(IOException::class)
    override fun serialize(serial: YamlSerializer) {
        mVersionCode?.let { serial.writeInt("versionCode", it) }
        mVersionName?.let { serial.writeString("versionName", it) }
    }

    /** versionCode（未设置为 -1）。 */
    fun getVersionCode(): Int = mVersionCode ?: -1

    /** 设置 versionCode。 */
    fun setVersionCode(versionCode: Int) {
        mVersionCode = versionCode
    }

    /** versionName 字符串（可 null）。 */
    var versionName: String?
        get() = mVersionName
        set(value) {
            mVersionName = value
        }
}
