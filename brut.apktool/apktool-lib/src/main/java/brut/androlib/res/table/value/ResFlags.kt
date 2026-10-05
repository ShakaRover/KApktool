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
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Arrays
import java.util.Comparator

/**
 * 标志位属性（format="flags"）：符号值为可组合的位掩码。
 *
 * [getSymbols] 用贪心集合覆盖把整数值分解为符号位组合：
 * 按"置位数升序、值升序"排序后逐项覆盖，缺口即失败（缓存 null）；
 * 再做冗余过滤（不能贡献新位的符号剔除）。
 */
class ResFlags(
    parent: ResReference?,
    type: Int,
    min: Int,
    max: Int,
    l10n: Int,
    private val mSymbols: Array<Symbol>,
) : ResAttribute(parent, type, min, max, l10n) {
    private var mSymbolsCache: HashMap<Int, Array<Symbol>?>? = null
    private var mFormatsCache: HashMap<Int, String?>? = null
    private var mSortedSymbols: Array<Symbol>? = null

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
                Log.w(TAG, "Unresolved flag symbol reference: $key")
                continue
            }

            Log.d(TAG, "Injecting dummy for unresolved flag symbol reference: $key")
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

    /** 把 data 分解为符号位组合；带缓存（缺失结果同样缓存为 null）。 */
    private fun getSymbols(data: Int): Array<Symbol>? {
        var cache = mSymbolsCache
        if (cache == null) {
            cache = HashMap()
            mSymbolsCache = cache
        } else if (cache.containsKey(data)) {
            return cache[data]
        }

        var sorted = mSortedSymbols
        if (sorted == null) {
            // 懒建立标志位优先级序：尽力还原源码书写顺序，无法完全精确。
            sorted = mSymbols.clone()
            val byBitCount = Comparator.comparingInt { s: Symbol -> Integer.bitCount(s.value.getData()) }
            val byRawValue = Comparator.comparingInt { s: Symbol -> s.value.getData() }
            sorted.sortWith(byBitCount.reversed().thenComparing(byRawValue))
            mSortedSymbols = sorted
        }

        var symbols = arrayOfNulls<Symbol>(sorted.size)
        var symbolsCount = 0

        if (data == 0) {
            for (symbol in sorted) {
                if (symbol.value.getData() == 0) {
                    symbols[symbolsCount++] = symbol
                }
            }
        } else {
            var mask = 0

            for (symbol in sorted) {
                val flag = symbol.value.getData()
                if (data and flag != flag || mask and flag == flag) {
                    continue
                }

                symbols[symbolsCount++] = symbol
                mask = mask or flag

                if (mask == data) {
                    break
                }
            }

            // 任一标志位缺少符号则提前失败。
            if (mask != data) {
                cache[data] = null
                return null
            }

            // 过滤冗余符号。
            if (symbolsCount > 2) {
                var filtered = arrayOfNulls<Symbol>(symbolsCount)
                var filteredCount = 0

                for (i in 0 until symbolsCount) {
                    val symbol = symbols[i]
                    mask = 0

                    // 合并其余符号的位。
                    for (j in 0 until symbolsCount) {
                        if (j != i) {
                            mask = mask or symbols[j]!!.value.getData()
                        }
                    }

                    // 不能贡献新位的剔除。
                    if (symbol!!.value.getData() and mask.inv() == 0) {
                        continue
                    }

                    filtered[filteredCount++] = symbol
                }

                symbols = filtered
                symbolsCount = filteredCount
            }
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
            val sb = StringBuilder()

            for (symbol in symbols) {
                val keySpec = symbol.key.resolve() ?: continue

                if (sb.isNotEmpty()) {
                    sb.append('|')
                }
                sb.append(keySpec.name)
            }

            formatted = sb.toString()
        }

        cache[data] = formatted
        return formatted
    }

    @Throws(AndrolibException::class, IOException::class)
    override fun serializeSymbolsToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        for (symbol in mSymbols) {
            val keySpec = symbol.key.resolve() ?: continue

            serial.startTag(null, "flag")
            serial.attribute(null, "name", keySpec.name)
            serial.attribute(null, "value", symbol.value.toXmlAttributeValue())
            serial.endTag(null, "flag")
        }
    }

    override fun toString(): String = String.format(
        "ResFlags{parent=%s, type=0x%04x, min=%s, max=%s, l10n=%s, symbols=%s}",
        mParent, mType, mMin, mMax, mL10n, Arrays.toString(mSymbols)
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResFlags
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
        private val TAG = ResFlags::class.java.name
    }
}
