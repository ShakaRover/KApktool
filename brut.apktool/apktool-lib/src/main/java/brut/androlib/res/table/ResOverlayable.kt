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

import brut.androlib.exceptions.UndefinedResObjectException
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Arrays

/**
 * overlayable 资源组（RRO 运行时资源覆盖框架的可见性声明）。
 *
 * 每个 policy 由标志位（public/system/vendor 等分区）与受控条目 ID 列表组成；
 * [serializeToXml] 输出 overlayable.xml 片段，无法解析的条目会被跳过并告警。
 */
class ResOverlayable(
    private val mPackage: ResPackage,
    private val mName: String,
    private val mActor: String,
) {
    private val mPolicies = ArrayList<Policy>()

    /** 所属包。 */
    val `package`: ResPackage
        get() = mPackage

    /** overlayable 名。 */
    fun getName(): String = mName

    /** 授权主体（可为空串表示无限制）。 */
    fun getActor(): String = mActor

    /** 追加一条策略。 */
    fun addPolicy(flags: Int, entries: Array<ResId>?) {
        mPolicies.add(Policy(flags, entries))
    }

    /** 写出 overlayable XML 片段；无策略时不输出。 */
    @Throws(IOException::class)
    fun serializeToXml(serial: XmlSerializer) {
        if (mPolicies.isEmpty()) {
            return
        }

        serial.startTag(null, "overlayable")
        serial.attribute(null, "name", mName)
        if (mActor.isNotEmpty()) {
            serial.attribute(null, "actor", mActor)
        }

        for (policy in mPolicies) {
            val type = renderType(policy.mFlags)
            val entrySpecs = resolveEntries(policy.mEntries)
            if (type == null || entrySpecs == null) {
                continue
            }

            serial.startTag(null, "policy")
            serial.attribute(null, "type", type)

            for (entrySpec in entrySpecs) {
                serial.startTag(null, "item")
                serial.attribute(null, "type", entrySpec.getTypeSpec().name)
                serial.attribute(null, "name", entrySpec.name)
                serial.endTag(null, "item")
            }

            serial.endTag(null, "policy")
        }

        serial.endTag(null, "overlayable")
    }

    /** 把标志位渲染为 'a|b|c' 名称串；无有效位时 null。 */
    private fun renderType(flags: Int): String? {
        if (flags == FLAG_NONE) {
            return null
        }
        val sb = StringBuilder()
        for (i in FLAG_MASKS.indices) {
            if (flags and FLAG_MASKS[i] != 0) {
                if (sb.isNotEmpty()) {
                    sb.append('|')
                }
                sb.append(FLAG_NAMES[i])
            }
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** 把条目 ID 解析为规格数组；NULL 与未定义 ID 跳过。 */
    private fun resolveEntries(entries: Array<ResId>?): Array<ResEntrySpec>? {
        if (entries == null || entries.isEmpty()) {
            return null
        }

        var entrySpecs = arrayOfNulls<ResEntrySpec>(entries.size)
        var entrySpecsCount = 0

        for (resId in entries) {
            if (resId == ResId.NULL) {
                continue
            }

            val entrySpec = try {
                mPackage.getEntrySpec(resId.typeId(), resId.entryId())
            } catch (ignored: UndefinedResObjectException) {
                Log.w(TAG, "Unresolved overlayable entry ID: $resId")
                continue
            }

            entrySpecs[entrySpecsCount++] = entrySpec
        }

        if (entrySpecsCount < entrySpecs.size) {
            entrySpecs = entrySpecs.copyOf(entrySpecsCount)
        }

        @Suppress("UNCHECKED_CAST")
        return entrySpecs as Array<ResEntrySpec>
    }

    override fun toString(): String =
        String.format("ResOverlayable{pkg=%s, name=%s, actor=%s}", mPackage, mName, mActor)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResOverlayable
        return mPackage == that.mPackage && mName == that.mName && mActor == that.mActor
    }

    override fun hashCode(): Int {
        var result = mPackage.hashCode()
        result = 31 * result + mName.hashCode()
        result = 31 * result + mActor.hashCode()
        return result
    }

    /** 单条策略：标志位 + 条目 ID 数组。 */
    private class Policy(val mFlags: Int, val mEntries: Array<ResId>?) {
        override fun toString(): String =
            String.format("Policy{flags=0x%08x, entries=%s}", mFlags, Arrays.toString(mEntries))
    }

    companion object {
        private val TAG = ResOverlayable::class.java.name

        private const val FLAG_NONE = 0
        private const val FLAG_PUBLIC = 1 // 0x0001
        private const val FLAG_SYSTEM_PARTITION = 2 // 0x0002
        private const val FLAG_VENDOR_PARTITION = 4 // 0x0004
        private const val FLAG_PRODUCT_PARTITION = 8 // 0x0008
        private const val FLAG_SIGNATURE = 16 // 0x0010
        private const val FLAG_ODM_PARTITION = 32 // 0x0020
        private const val FLAG_OEM_PARTITION = 64 // 0x0040
        private const val FLAG_ACTOR_SIGNATURE = 128 // 0x0080
        private const val FLAG_CONFIG_SIGNATURE = 256 // 0x0100

        private val FLAG_MASKS = intArrayOf(
            FLAG_PUBLIC, FLAG_SYSTEM_PARTITION, FLAG_VENDOR_PARTITION, FLAG_PRODUCT_PARTITION,
            FLAG_SIGNATURE, FLAG_ODM_PARTITION, FLAG_OEM_PARTITION, FLAG_ACTOR_SIGNATURE,
            FLAG_CONFIG_SIGNATURE,
        )
        private val FLAG_NAMES = arrayOf(
            "public", "system", "vendor", "product", "signature", "odm", "oem", "actor",
            "config_signature",
        )
    }
}
