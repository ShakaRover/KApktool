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
 * 枚举属性（format="enum"）：符号名与整数一一对应。
 *
 * 符号与格式渲染结果均带缓存（值为 null 也缓存，避免重复查找）；
 * data == -1 时优先选择 match_parent 而非弃用的 fill_parent。
 */
class ResEnum(
    parent: ResReference?,
    type: Int,
    min: Int,
    max: Int,
    l10n: Int,
    private val mSymbols: Array<Symbol>,
) : ResAttribute(parent, type, min, max, l10n) {
    private var mSymbolsCache: HashMap<Int, Array<Symbol>?>? = null
    private var mFormatsCache: HashMap<Int, String?>? = null

    /** 为未解析的符号名注入 id 型 dummy 条目。 */
    @Throws(AndrolibException::class)
    override fun resolveKeys() {
        val pkg = mParent!!.`package`
        val skipUnresolved = pkg.getTable().config.isDecodeResolveLazy

        for (symbol in mSymbols) {
            val key = symbol.key
            if (key.resolve() != null) {
                continue
            }

            val keyId = key.getResId()

            // #2836 - 无法解析的符号直接跳过。
            if (skipUnresolved || keyId.pkgId() != pkg.getId()) {
                Log.w(TAG, "Unresolved enum symbol reference: $key")
                continue
            }

            Log.d(TAG, "Injecting dummy for unresolved enum symbol reference: $key")
            if (!pkg.hasTypeSpec(keyId.typeId())) {
                pkg.addTypeSpec(keyId.typeId(), "id")
                pkg.addType(keyId.typeId())
            }
            pkg.addEntrySpec(keyId.typeId(), keyId.entryId(), ResEntrySpec.DUMMY_PREFIX + keyId)
            pkg.addEntry(keyId.typeId(), keyId.entryId(), ResCustom.ID)
        }
    }

    override fun getSymbolsForValue(value: ResItem?): Array<Symbol>? {
        if (!isSymbolValueType(value)) {
            return null
        }
        return getSymbols((value as ResPrimitive).getData())
    }

    private fun isSymbolValueType(value: ResItem?): Boolean {
        if (value !is ResPrimitive) {
            return false
        }
        val type = value.getType()
        return type == ResValue.TYPE_INT_DEC || type == ResValue.TYPE_INT_HEX
    }

    /** 查找值等于 data 的符号；带缓存（缺失结果同样缓存为 null）。 */
    private fun getSymbols(data: Int): Array<Symbol>? {
        var cache = mSymbolsCache
        if (cache == null) {
            // 懒建立符号缓存以提速。
            cache = HashMap()
            mSymbolsCache = cache
        } else if (cache.containsKey(data)) {
            return cache[data]
        }

        val symbols = arrayOfNulls<Symbol>(mSymbols.size)
        var symbolsCount = 0

        for (symbol in mSymbols) {
            if (symbol.value.getData() == data) {
                symbols[symbolsCount++] = symbol
            }
        }

        // 值没有匹配符号则提前结束。
        if (symbolsCount == 0) {
            cache[data] = null
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val result = if (symbolsCount < symbols.size) {
            symbols.copyOf(symbolsCount) as Array<Symbol>
        } else {
            symbols as Array<Symbol>
        }
        cache[data] = result
        return result
    }

    @Throws(AndrolibException::class)
    override fun formatValueFromSymbols(value: ResItem?): String? {
        if (!isSymbolValueType(value)) {
            return null
        }

        val data = (value as ResPrimitive).getData()
        var cache = mFormatsCache
        if (cache == null) {
            cache = HashMap()
            mFormatsCache = cache
        } else if (cache.containsKey(data)) {
            return cache[data]
        }

        var formatted: String? = null
        getSymbols(data)?.let { symbols ->
            for (symbol in symbols) {
                val keySpec = symbol.key.resolve() ?: continue

                formatted = keySpec.name

                // fill_parent 自 API 8 起弃用但常排在首位；继续找 match_parent 并优先使用。
                if (data == -1 && formatted == "fill_parent") {
                    continue
                }
                break
            }
        }

        cache[data] = formatted
        return formatted
    }

    @Throws(AndrolibException::class, IOException::class)
    override fun serializeSymbolsToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        for (symbol in mSymbols) {
            val keySpec = symbol.key.resolve() ?: continue

            serial.startTag(null, "enum")
            serial.attribute(null, "name", keySpec.name)
            serial.attribute(null, "value", symbol.value.toXmlAttributeValue())
            serial.endTag(null, "enum")
        }
    }

    override fun toString(): String = String.format(
        "ResEnum{parent=%s, type=0x%04x, min=%s, max=%s, l10n=%s, symbols=%s}",
        mParent, mType, mMin, mMax, mL10n, Arrays.toString(mSymbols)
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResEnum
        return mParent == that.mParent && mType == that.mType && mMin == that.mMin &&
            mMax == that.mMax && mL10n == that.mL10n && mSymbols.contentEquals(that.mSymbols)
    }

    override fun hashCode(): Int {
        var result = mParent!!.hashCode()
        result = 31 * result + mType
        result = 31 * result + mMin
        result = 31 * result + mMax
        result = 31 * result + mL10n
        result = 31 * result + mSymbols.contentHashCode()
        return result
    }

    companion object {
        private val TAG = ResEnum::class.java.name
    }
}
