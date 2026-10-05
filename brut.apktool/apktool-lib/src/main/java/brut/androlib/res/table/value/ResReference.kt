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
package brut.androlib.res.table.value

import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.UndefinedResObjectException
import brut.androlib.res.table.ResEntry
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 * 引用型资源值（@ref / ?attr）。
 *
 * 解析（[resolve]）时先经 [fixDynamicResourceId] 修正共享库自引用的 0x00 包 ID；
 * 无法解析时安全退化为 @null 文本。
 */
class ResReference @JvmOverloads constructor(
    private val mPackage: ResPackage,
    private val mResId: ResId,
    asAttr: Boolean = false,
) : ResItem(if (asAttr) ResValue.TYPE_ATTRIBUTE else ResValue.TYPE_REFERENCE) {
    /** 是否按属性引用（?）而非普通引用（@）。 */
    private val mAsAttr: Boolean = asAttr
    /** 引用所在包。 */
    val `package`: ResPackage
        get() = mPackage

    /** 目标资源 ID。 */
    fun getResId(): ResId = mResId

    /** 解析为条目规格；失败返回 null。 */
    @Throws(AndrolibException::class)
    fun resolve(): brut.androlib.res.table.ResEntrySpec? {
        if (mResId != ResId.NULL) {
            val resId = fixDynamicResourceId(mResId)
            try {
                return mPackage.getTable().resolve(resId)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        return null
    }

    /** 解析为条目；失败返回 null。 */
    @Throws(AndrolibException::class)
    fun resolveEntry(): ResEntry? {
        if (mResId != ResId.NULL) {
            val resId = fixDynamicResourceId(mResId)
            try {
                return mPackage.getTable().resolveEntry(resId)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        return null
    }

    private fun fixDynamicResourceId(resId: ResId): ResId {
        if (resId.pkgId() == 0 && mPackage.getId() != 0) {
            // 包 ID 为 0x00 表示共享库引用自身本地资源，替换为调用方包 ID。
            return ResId.of(mPackage.getId(), resId.typeId(), resId.entryId())
        }
        return resId
    }

    @Throws(AndrolibException::class)
    override fun toXmlTextValue(): String {
        val spec = resolve()
        if (spec == null) {
            if (mResId != ResId.NULL) {
                Log.w(TAG, "Unresolved resource reference: $this")
            }
            // @null 是特殊原语而非真引用；解析失败时只能退回它。
            return "@null"
        }

        val includePackage = mPackage.getGroup() != spec.`package`.getGroup()
        val includeType = !mAsAttr || spec.getTypeSpec().name != "attr"
        return (if (mAsAttr) "?" else "@") +
            (if (includePackage) spec.`package`.getName() + ":" else "") +
            (if (includeType) spec.getTypeSpec().name + "/" else "") +
            spec.name
    }

    @Throws(AndrolibException::class, IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val typeName = entry.getType().getName()

        // 类型不直接支持该值格式时，序列化为通用 <item> 标签。
        val asItem = !entry.getType().getSpec().isValueCompatible(this)

        // 仅当能解析或条目是 <string> 时才写正文：
        // @null 是除 string 外所有条目类型的默认值；<id> 标签从不写 @null。
        val needsBody = resolve() != null || typeName == "string"

        val tagName = if (asItem) "item" else typeName
        serial.startTag(null, tagName)
        if (asItem) {
            serial.attribute(null, "type", typeName)
        }
        serial.attribute(null, "name", entry.getName())
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }
        if (needsBody) {
            serial.text(toXmlTextValue())
        }
        serial.endTag(null, tagName)
    }

    override fun toString(): String =
        String.format("ResReference{pkg=%s, id=%s, type=%s}", mPackage, mResId, if (mAsAttr) "attr" else "ref")

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResReference
        return mPackage == that.mPackage && mResId == that.mResId && mAsAttr == that.mAsAttr
    }

    override fun hashCode(): Int {
        var result = mPackage.hashCode()
        result = 31 * result + mResId.hashCode()
        result = 31 * result + mAsAttr.hashCode()
        return result
    }

    companion object {
        private val TAG = ResReference::class.java.name
    }
}
