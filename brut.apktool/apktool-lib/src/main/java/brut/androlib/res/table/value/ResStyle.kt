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
import brut.androlib.res.table.ResEntry
import brut.androlib.res.table.ResEntrySpec
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Arrays

/**
 * style 资源袋：key 为属性引用，value 为属性值。
 *
 * [resolveKeys] 为无法解析的自有属性注入 dummy 条目（#2836）；
 * 序列化时默认跳过重复 key（#3400），分析模式下全部保留。
 */
class ResStyle(
    parent: ResReference?,
    private val mItems: Array<Item>,
) : ResBag(parent) {
    /** 样式条目：属性引用 key + 值。 */
    class Item(
        /** 属性引用（生成 getKey()）。 */
        val key: ResReference,
        /** 属性值（生成 getValue()）。 */
        val value: ResItem?,
    ) {
        override fun toString(): String = "Item{key=$key, value=$value}"
    }

    /** 为无法解析的属性 key 注入占位条目。 */
    @Throws(AndrolibException::class)
    override fun resolveKeys() {
        val pkg = mParent!!.`package`
        val skipUnresolved = pkg.getTable().config.isDecodeResolveLazy

        for (item in mItems) {
            val key = item.key
            if (key.resolveEntry() != null) {
                continue
            }

            val keyId = key.getResId()

            // #2836 - 无法解析的条目直接跳过。
            if (skipUnresolved || keyId.pkgId() != pkg.getId()) {
                Log.w(TAG, "Unresolved style item reference: $key")
                continue
            }

            Log.d(TAG, "Injecting dummy for unresolved style item reference: $key")
            if (!pkg.hasTypeSpec(keyId.typeId())) {
                pkg.addTypeSpec(keyId.typeId(), "attr")
                pkg.addType(keyId.typeId())
            }
            pkg.addEntrySpec(keyId.typeId(), keyId.entryId(), ResEntrySpec.DUMMY_PREFIX + keyId)
            pkg.addEntry(keyId.typeId(), keyId.entryId(), ResAttribute.DEFAULT)
        }
    }

    @Throws(AndrolibException::class, IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val tagName = "style"
        serial.startTag(null, tagName)
        serial.attribute(null, "name", entry.getName())
        val parent = mParent!!
        if (parent.resolve() != null) {
            serial.attribute(null, "parent", parent.toXmlAttributeValue())
        } else if (entry.getName().contains('.')) {
            serial.attribute(null, "parent", "")
        }
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }

        val pkg = parent.`package`
        val skipDuplicates = !pkg.getTable().config.isAnalysisMode
        val processedKeys = HashSet<ResId>()
        for (item in mItems) {
            val key = item.key
            val keyEntry = key.resolveEntry() ?: continue

            val keyId = key.getResId()

            // #3400 - 样式中重复的 item 只保留第一次出现。
            if (skipDuplicates && !processedKeys.add(keyId)) {
                continue
            }

            val includePackage = pkg.getGroup() != keyEntry.`package`.getGroup()
            val keyName = (if (includePackage) keyEntry.`package`.getName() + ":" else "") + keyEntry.getName()

            val body = if (keyEntry.value is ResAttribute) {
                // 用属性条目自身的值格式渲染。
                (keyEntry.value as ResAttribute).formatAsTextValue(item.value)
            } else {
                Log.w(TAG, "Unexpected style item key: $keyEntry")
                // 退回到默认属性格式渲染。
                ResAttribute.DEFAULT.formatAsTextValue(item.value)
            }

            serial.startTag(null, "item")
            serial.attribute(null, "name", keyName)
            serial.text(body)
            serial.endTag(null, "item")
        }

        serial.endTag(null, tagName)
    }

    override fun toString(): String = "ResStyle{parent=$mParent, items=${Arrays.toString(mItems)}}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return mParent == (other as ResStyle).mParent && mItems.contentEquals(other.mItems)
    }

    override fun hashCode(): Int = 31 * (mParent?.hashCode() ?: 0) + mItems.contentHashCode()

    companion object {
        private val TAG = ResStyle::class.java.name

        /** 把原始条目 key 包装为属性引用并构造样式袋。 */
        @JvmStatic
        fun parse(parent: ResReference?, rawItems: Array<RawItem>): ResStyle {
            val items = arrayOfNulls<Item>(rawItems.size)
            val pkg = parent!!.`package`

            for (i in rawItems.indices) {
                val rawItem = rawItems[i]
                // key 是指向 XML 属性的引用。
                val key = ResReference(pkg, ResId.of(rawItem.key))
                items[i] = Item(key, rawItem.value)
            }

            @Suppress("UNCHECKED_CAST")
            return ResStyle(parent, items as Array<Item>)
        }
    }
}
