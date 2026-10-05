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
 * apktool.yml 的 usesFramework 节：依赖的 framework 包 ID 列表与版本 tag。
 */
class UsesFramework : YamlSerializable {
    private val mIds = ArrayList<Int>()
    private var mTag: String? = null

    /** 清空全部字段。 */
    fun clear() {
        mIds.clear()
        mTag = null
    }

    /** 是否没有任何框架声明。 */
    val isEmpty: Boolean
        get() = mIds.isEmpty() && mTag == null

    @Throws(IOException::class)
    override fun onEntry(parser: YamlPullParser) {
        when (parser.getKey()) {
            "ids" -> {
                mIds.clear()
                parser.readIntSeq(mIds)
            }
            "tag" -> mTag = parser.getString()
        }
    }

    @Throws(IOException::class)
    override fun serialize(serial: YamlSerializer) {
        if (mIds.isNotEmpty()) {
            serial.writeIntSeq("ids", mIds)
        }
        mTag?.let { serial.writeString("tag", it) }
    }

    /** framework 包 ID 列表（可变引用，与原实现一致）。 */
    val ids: MutableList<Int>
        get() = mIds

    /** 框架版本 tag（如 "15"）。 */
    var tag: String?
        get() = mTag
        set(value) {
            mTag = value
        }
}
