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
 * apktool.yml 的 resourcesInfo 节：包 ID/包名与 sparse/compact/keepRaw 标记。
 *
 * 布尔标记用可空包装表达"未设置"；读取时按默认值回退（packageId=-1，布尔=false）。
 */
class ResourcesInfo : YamlSerializable {
    private var mPackageId: Int? = null
    private var mPackageName: String? = null
    private var mSparseEntries: Boolean? = null
    private var mCompactEntries: Boolean? = null
    private var mKeepRawValues: Boolean? = null

    /** 清空全部字段。 */
    fun clear() {
        mPackageId = null
        mPackageName = null
        mSparseEntries = null
        mCompactEntries = null
        mKeepRawValues = null
    }

    /** 是否没有任何资源信息。 */
    val isEmpty: Boolean
        get() = mPackageId == null && mPackageName == null && mSparseEntries == null &&
            mCompactEntries == null && mKeepRawValues == null

    @Throws(IOException::class)
    override fun onEntry(parser: YamlPullParser) {
        when (parser.getKey()) {
            "packageId" -> mPackageId = parser.getInt()
            "packageName" -> mPackageName = parser.getString()
            "sparseEntries" -> mSparseEntries = parser.getBool()
            "compactEntries" -> mCompactEntries = parser.getBool()
            "keepRawValues" -> mKeepRawValues = parser.getBool()
        }
    }

    @Throws(IOException::class)
    override fun serialize(serial: YamlSerializer) {
        mPackageId?.let { serial.writeInt("packageId", it) }
        mPackageName?.let { serial.writeString("packageName", it) }
        mSparseEntries?.let { serial.writeBool("sparseEntries", it) }
        mCompactEntries?.let { serial.writeBool("compactEntries", it) }
        mKeepRawValues?.let { serial.writeBool("keepRawValues", it) }
    }

    /** 包 ID（未设置为 -1）。 */
    fun getPackageId(): Int = mPackageId ?: -1

    /** 设置包 ID。 */
    fun setPackageId(packageId: Int) {
        mPackageId = packageId
    }

    /** 应用包名。 */
    var packageName: String?
        get() = mPackageName
        set(value) {
            mPackageName = value
        }

    /** 资源表是否使用稀疏条目编码。 */
    fun isSparseEntries(): Boolean = mSparseEntries ?: false

    /** 设置稀疏条目标记。 */
    fun setSparseEntries(sparseEntries: Boolean) {
        mSparseEntries = sparseEntries
    }

    /** 资源表是否使用紧凑（稀疏）条目编码。 */
    fun isCompactEntries(): Boolean = mCompactEntries ?: false

    /** 设置紧凑条目标记。 */
    fun setCompactEntries(compactEntries: Boolean) {
        mCompactEntries = compactEntries
    }

    /** 是否保留原始值解码模式。 */
    fun isKeepRawValues(): Boolean = mKeepRawValues ?: false

    /** 设置保留原始值标记。 */
    fun setKeepRawValues(keepRawValues: Boolean) {
        mKeepRawValues = keepRawValues
    }
}
