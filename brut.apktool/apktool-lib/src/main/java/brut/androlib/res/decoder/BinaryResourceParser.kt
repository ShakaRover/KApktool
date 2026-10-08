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
package brut.androlib.res.decoder

import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.UndefinedResObjectException
import brut.androlib.res.data.FeatureFlag
import brut.androlib.res.data.ResChunkHeader
import brut.androlib.res.data.ResStringPool
import brut.androlib.res.table.ResConfig
import brut.androlib.res.table.ResEntrySpec
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.table.ResTable
import brut.androlib.res.table.ResTypeSpec
import brut.androlib.res.table.value.ResBag
import brut.androlib.res.table.value.ResCustom
import brut.androlib.res.table.value.ResFileReference
import brut.androlib.res.table.value.ResItem
import brut.androlib.res.table.value.ResPrimitive
import brut.androlib.res.table.value.ResReference
import brut.androlib.res.table.value.ResString
import brut.androlib.res.table.value.ResValue
import brut.common.Log
import brut.util.BinaryDataInputStream
import brut.util.TextUtils
import java.io.BufferedInputStream
import java.util.TreeMap
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * resources.arsc 二进制解析器（资源表的完整建模入口）。
 *
 * 按 chunk 层级解析：table -> 3 个字符串池 / flag 列表 -> package ->
 * typeSpec / type（常规、稀疏、16 位偏移三种编码）/ flagged / library /
 * overlayable / staged aliases；解析 ResTable_config 生成限定词。
 *
 * 大量混淆与畸形样本的容错策略（对应上游 issue 号）：
 * #3311 无 typeSpec 的 type、#3372 16 位偏移、#3428 对齐填充、
 * #3778 乱序条目（按偏移预排序）、#2824 重复畸形条目跳过、
 * #3993 feature flag 引用校验。缺失条目规格可注入 dummy（[injectDummyEntrySpecs]）。
 */
class BinaryResourceParser(
    private val mTable: ResTable,
    private val mKeepBrokenResources: Boolean,
    private val mAllowDummyEntrySpecs: Boolean,
) {
    private val mValueStringPool = ResStringPool()
    private val mTypeStringPool = ResStringPool()
    private val mKeyStringPool = ResStringPool()
    private val mMissingEntrySpecs = HashSet<ResId>()
    private val mInvalidConfigs = HashSet<ResConfig>()

    private var mIn: BinaryDataInputStream? = null
    private var mFlagMap: MutableMap<Int, String>? = null
    private var mPackageCount = 0
    private var mPackage: ResPackage? = null
    private var mTypeIdOffset = 0
    private var mFlag: FeatureFlag? = null
    private var mHasSparseEntries = false
    private var mHasCompactEntries = false
    private var mEntrySpecFlagsOffsets: MutableList<Pair<Long, Int>>? = null

    /** 资源表是否含稀疏编码条目。 */
    fun hasSparseEntries(): Boolean = mHasSparseEntries

    /** 资源表是否含紧凑（内联）编码条目。 */
    fun hasCompactEntries(): Boolean = mHasCompactEntries

    /** 已收集的 feature flag 名（未启用收集时为 null）。 */
    fun getFlags(): Collection<String>? = mFlagMap?.values

    /** 开启 typeSpec flags 区块偏移收集（framework publicize 用）。 */
    fun enableCollectFlagsOffsets() {
        mEntrySpecFlagsOffsets = ArrayList()
    }

    /** flags 区块 (偏移, 条目数) 列表。 */
    fun getEntrySpecFlagsOffsets(): Collection<Pair<Long, Int>>? = mEntrySpecFlagsOffsets

    /** 解析整个 arsc 流。 */
    @Throws(AndrolibException::class)
    fun parse(`in`: InputStream?) {
        reset()
        val input = BinaryDataInputStream(BufferedInputStream(`in`))
        mIn = input

        var parser = ResChunkPullParser(input)
        try {
            if (!nextChunk(parser)) {
                throw AndrolibException("Input file is empty.")
            }
            if (parser.chunkType() != ResChunkHeader.RES_TABLE_TYPE) {
                throw AndrolibException(
                    "Unexpected chunk: " + parser.chunkName() + " (expected: RES_TABLE_TYPE)"
                )
            }

            parseTable(parser)

            Log.d(TAG, "End of chunks at 0x%08x", input.position())

            // 主流长度未知，这里用 available() 判断尾随数据。
            if (input.available() > 0) {
                Log.d(TAG, "Ignoring trailing data at 0x%08x.", input.position())
            }
        } catch (ex: IOException) {
            throw AndrolibException("Could not decode arsc file.", ex)
        }
    }

    /** 复位全部解析状态（字符串池、临时表）。 */
    fun reset() {
        mIn = null
        mValueStringPool.reset()
        mTypeStringPool.reset()
        mKeyStringPool.reset()
        mMissingEntrySpecs.clear()
        mInvalidConfigs.clear()
        mFlagMap = null
        mPackageCount = 0
        mPackage = null
        mTypeIdOffset = 0
        mFlag = null
        mHasSparseEntries = false
        mHasCompactEntries = false
        mEntrySpecFlagsOffsets?.clear()
    }

    /** 前进到下一个受支持 chunk（跳过 NULL 类型与填充）。 */
    @Throws(IOException::class)
    private fun nextChunk(parser: ResChunkPullParser): Boolean {
        // 跳过当前 chunk 尾部填充或未知数据。
        if (parser.isChunk()) {
            val skipped = parser.skipChunk()
            if (skipped > 0) {
                Log.d(TAG, "Skipped unknown %s bytes at end of %s chunk.", skipped, parser.chunkName())
            }
        }

        while (parser.next()) {
            // 跳过未知或不支持的 chunk。
            if (parser.chunkType() == ResChunkHeader.RES_NULL_TYPE) {
                Log.d(TAG, "Skipping unknown chunk (%s) of %s bytes at 0x%08x.",
                    parser.chunkName(), parser.chunkSize(), parser.chunkStart())
                parser.skipChunk()
                continue
            }

            Log.d(TAG, "Chunk at 0x%08x: %s (%s bytes)",
                parser.chunkStart(), parser.chunkName(), parser.chunkSize())
            return true
        }

        return false
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseTable(parser: ResChunkPullParser) {
        // ResTable_header
        val packageCount = mIn!!.readInt()

        skipUnreadHeader(parser)

        var inner = ResChunkPullParser(mIn!!, parser.dataSize())
        while (nextChunk(inner)) {
            when (inner.chunkType()) {
                ResChunkHeader.RES_STRING_POOL_TYPE -> parseStringPool(inner)
                ResChunkHeader.RES_TABLE_FLAG_LIST -> parseFlagList(inner)
                ResChunkHeader.RES_TABLE_PACKAGE_TYPE -> parsePackage(inner)
                else -> skipUnexpectedChunk(inner)
            }
        }

        if (mPackageCount != packageCount) {
            Log.w(TAG, "Unexpected package count: %s (expected: %s)", mPackageCount, packageCount)
        }
    }

    /** 三个字符串池按出现顺序依次装填。 */
    @Throws(AndrolibException::class, IOException::class)
    private fun parseStringPool(parser: ResChunkPullParser) {
        when {
            !mValueStringPool.isLoaded -> mValueStringPool.parse(parser)
            !mTypeStringPool.isLoaded -> mTypeStringPool.parse(parser)
            !mKeyStringPool.isLoaded -> mKeyStringPool.parse(parser)
            else -> skipUnexpectedChunk(parser)
        }
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseFlagList(parser: ResChunkPullParser) {
        // ResTable_flag_list
        val count = parser.dataSize() / 4
        if (count == 0) {
            return
        }
        if (mFlagMap != null) {
            skipUnexpectedChunk(parser)
            return
        }
        if (!mValueStringPool.isLoaded) {
            throw AndrolibException("Missing value string pool.")
        }

        skipUnreadHeader(parser)

        val flagMap = HashMap<Int, String>()
        mFlagMap = flagMap

        // 全部读写特性 flag 名称的索引数组。
        for (i in 0 until count) {
            val flagNameIndex = mIn!!.readInt()

            // 混淆器可能注入假索引滥用 feature flag。
            val flagName = mValueStringPool.getString(flagNameIndex)
            if (flagName == null) {
                Log.d(TAG, "Skipping invalid declared feature flag index: 0x%08x", flagNameIndex)
                continue
            }

            flagMap[flagNameIndex] = flagName
        }
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parsePackage(parser: ResChunkPullParser) {
        val input = mIn!!
        // ResTable_package
        val id = input.readInt()
        val name = input.readUtf16(128)
        input.skipInt() // typeStrings
        input.skipInt() // lastPublicType
        input.skipInt() // keyStrings
        input.skipInt() // lastPublicKey

        // TypeIdOffset 仅新式/分包应用存在（platform f90f2f8 之后）。
        // sizeof(ResTable_package) = short + short + int + int + char[128] + int * 5 = 288
        if (parser.headerSize() >= 288) {
            mTypeIdOffset = input.readInt()

            if (mTypeIdOffset > 0) {
                Log.w(TAG, "Please report this app here: https://github.com/iBotPeaches/Apktool/issues/1728")
            }
        } else {
            mTypeIdOffset = 0
        }

        skipUnreadHeader(parser)

        val pkg = try {
            mTable.getPackageGroup(id).addSubPackage()
        } catch (ignored: UndefinedResObjectException) {
            mTable.addPackageGroup(id, name).getBasePackage()
        } finally {
            mPackageCount++
        }
        mPackage = pkg

        var inner = ResChunkPullParser(input, parser.dataSize())
        while (nextChunk(inner)) {
            when (inner.chunkType()) {
                ResChunkHeader.RES_STRING_POOL_TYPE -> parseStringPool(inner)
                ResChunkHeader.RES_TABLE_TYPE_SPEC_TYPE -> parseTypeSpec(inner)
                ResChunkHeader.RES_TABLE_TYPE_TYPE -> parseType(inner)
                ResChunkHeader.RES_TABLE_FLAGGED -> parseFlagged(inner)
                ResChunkHeader.RES_TABLE_LIBRARY_TYPE -> parseLibrary(inner)
                ResChunkHeader.RES_TABLE_OVERLAYABLE_TYPE -> parseOverlayable(inner)
                ResChunkHeader.RES_TABLE_STAGED_ALIAS_TYPE -> parseStagedAliases(inner)
                else -> skipUnexpectedChunk(inner)
            }
        }

        // 清理本包状态。
        injectDummyEntrySpecs()
        mTypeStringPool.reset()
        mKeyStringPool.reset()
        mInvalidConfigs.clear()
        mPackage = null
        mTypeIdOffset = 0
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseTypeSpec(parser: ResChunkPullParser) {
        if (!mTypeStringPool.isLoaded) {
            throw AndrolibException("Missing type string pool.")
        }
        val input = mIn!!

        // ResTable_typeSpec
        val id = input.readUnsignedByte()
        input.skipByte() // res0
        input.skipShort() // typesCount
        val entryCount = input.readInt()

        skipUnreadHeader(parser)

        mEntrySpecFlagsOffsets?.add(Pair(input.position(), entryCount))
        input.skipBytes(entryCount * 4) // flags

        mPackage!!.addTypeSpec(id, mTypeStringPool.getString(id - 1)!!)
    }

    @Suppress("MANY_CONDITIONAL_CHAINS_WHEN")
    @Throws(AndrolibException::class, IOException::class)
    private fun parseType(parser: ResChunkPullParser) {
        if (!mTypeStringPool.isLoaded) {
            throw AndrolibException("Missing type string pool.")
        }
        if (!mKeyStringPool.isLoaded) {
            throw AndrolibException("Missing key string pool.")
        }
        val input = mIn!!
        val pkg = mPackage!!

        // ResTable_type
        val id = input.readUnsignedByte() - mTypeIdOffset
        val flags = input.readUnsignedByte()
        input.skipShort() // reserved
        var entryCount = input.readInt()
        val entriesStart = input.readInt()
        val config = parseConfig()

        skipUnreadHeader(parser)

        // #3311 - 部分老应用没有 TYPE_SPEC chunk 却直接定义 TYPE。
        val typeSpec = try {
            pkg.getTypeSpec(id)
        } catch (ignored: UndefinedResObjectException) {
            pkg.addTypeSpec(id, mTypeStringPool.getString(id - 1)!!)
        }

        val typeName = typeSpec.name
        var type: brut.androlib.res.table.ResType? = null
        if (mInvalidConfigs.contains(config)) {
            if (mKeepBrokenResources) {
                Log.w(TAG, "Keeping resources for invalid resource config: typeName=%s, config=%s, flag=%s",
                    typeName, config, mFlag)
                type = pkg.addType(id, config, mFlag)
            } else {
                Log.w(TAG, "Dropping resources for invalid resource config: typeName=%s, config=%s, flag=%s",
                    typeName, config, mFlag)
                type = null
            }
        } else {
            type = pkg.addType(id, config, mFlag)
        }

        val isOffset16 = flags and TYPE_FLAG_OFFSET16 != 0
        val isSparse = flags and TYPE_FLAG_SPARSE != 0

        if (isSparse) {
            mHasSparseEntries = true
        }

        // #3778 - 条目可能乱序、需要向前跳；统一按偏移预排序。
        val entryOffsets = TreeMap<Int, MutableList<Int>>()
        for (i in 0 until entryCount) {
            val index: Int
            var offset: Int

            // #3372 - 16 位偏移要换算成真实偏移（* 4u）。
            if (isSparse) {
                index = input.readUnsignedShort()
                offset = input.readUnsignedShort() * 4
            } else {
                index = i

                if (isOffset16) {
                    offset = input.readUnsignedShort()
                    offset = if (offset == NO_ENTRY_OFFSET16) NO_ENTRY else offset * 4
                } else {
                    offset = input.readInt()
                }
            }

            entryOffsets.getOrPut(offset) { ArrayList() }.add(index)
        }

        // 剔除 NO_ENTRY 占位。
        var indexes = entryOffsets[NO_ENTRY]
        if (indexes != null) {
            if (type != null) {
                for (index in indexes) {
                    if (!pkg.hasEntrySpec(id, index)) {
                        mMissingEntrySpecs.add(ResId.of(pkg.getId(), id, index))
                    }
                }
            }

            entryOffsets.remove(NO_ENTRY)
            // 更新计数仅用于日志。
            entryCount -= indexes.size
        }

        // 解析其余条目。
        for ((offset, idxs) in entryOffsets) {
            indexes = idxs

            // #3428 - 条目可能为对齐做填充；#3778 证明对齐到条目区起始能同时兼容两种情形。
            val entryStart = parser.chunkStart() + entriesStart + offset.toLong()

            // 近年的 APK 中条目数可能超过 chunk 实际容量。
            if (entryStart >= parser.chunkEnd()) {
                Log.w(TAG, "End of chunk hit. Skipping remaining %s entries in type: %s", entryCount, typeName)
                break
            }

            // 与条目起点强制对齐。
            input.jumpTo(entryStart)

            val entry = parseEntry(typeName)!!
            val key: Int = entry.first
            val value: ResValue? = entry.second

            // 无效配置下 type 为 null，值直接丢弃。
            if (type != null) {
                for (index in indexes) {
                    val resId = ResId.of(pkg.getId(), id, index)

                    // #2824 - 重复条目中第二个常为畸形，AOSP 直接跳过。
                    if (value == null) {
                        if (!pkg.hasEntrySpec(id, index)) {
                            mMissingEntrySpecs.add(resId)
                        }
                        continue
                    }

                    // 同一条目不允许重复添加。
                    if (pkg.hasEntry(id, index, config, mFlag)) {
                        Log.w(TAG, "Ignoring repeated entry: id=%s, config=%s, flag=%s", resId, config, mFlag)
                        continue
                    }

                    if (!pkg.hasEntrySpec(id, index)) {
                        pkg.addEntrySpec(id, index, mKeyStringPool.getString(key)!!)
                        mMissingEntrySpecs.remove(resId)
                    }
                    pkg.addEntry(id, index, config, mFlag, value)
                }
            }

            // 更新计数仅用于日志。
            entryCount -= indexes.size
        }
    }

    /** 解析 ResTable_config（各版本 size 增量兼容）。 */
    @Throws(AndrolibException::class, IOException::class)
    private fun parseConfig(): ResConfig {
        val input = mIn!!
        val startPosition = input.position()
        // ResTable_config
        val size = input.readInt()
        if (size < 8) {
            throw AndrolibException("Config size < 8")
        }

        val mcc = input.readUnsignedShort()
        val mnc = input.readUnsignedShort()

        var language = ""
        var region = ""
        if (size >= 12) {
            language = unpackLanguageOrRegion(input.readBytes(2), 'a')
            region = unpackLanguageOrRegion(input.readBytes(2), '0')
        }

        var orientation = 0
        var touchscreen = 0
        if (size >= 14) {
            orientation = input.readUnsignedByte()
            touchscreen = input.readUnsignedByte()
        }

        var density = 0
        if (size >= 16) {
            density = input.readUnsignedShort()
        }

        var keyboard = 0
        var navigation = 0
        var inputFlags = 0
        var grammaticalInflection = 0
        if (size >= 20) {
            keyboard = input.readUnsignedByte()
            navigation = input.readUnsignedByte()
            inputFlags = input.readUnsignedByte()
            grammaticalInflection = input.readUnsignedByte()
        }

        var screenWidth = 0
        var screenHeight = 0
        var sdkVersion = 0
        var minorVersion = 0
        if (size >= 28) {
            screenWidth = input.readUnsignedShort()
            screenHeight = input.readUnsignedShort()
            sdkVersion = input.readUnsignedShort()
            minorVersion = input.readUnsignedShort()
        }

        var screenLayout = 0
        var uiMode = 0
        var smallestScreenWidthDp = 0
        if (size >= 32) {
            screenLayout = input.readUnsignedByte()
            uiMode = input.readUnsignedByte()
            smallestScreenWidthDp = input.readUnsignedShort()
        }

        var screenWidthDp = 0
        var screenHeightDp = 0
        if (size >= 36) {
            screenWidthDp = input.readUnsignedShort()
            screenHeightDp = input.readUnsignedShort()
        }

        var localeScript = ""
        var localeVariant = ""
        if (size >= 48) {
            localeScript = input.readAscii(4)
            localeVariant = input.readAscii(8)
        }

        var screenLayout2 = 0
        var colorMode = 0
        if (size >= 52) {
            screenLayout2 = input.readUnsignedByte()
            colorMode = input.readUnsignedByte()
            input.skipShort() // screenConfigPad2
        }

        // 此处以后为非标准数据。
        val bytesRead = (input.position() - startPosition).toInt()
        val unknown = readExceedingBytes("Config", size, bytesRead)

        val config = ResConfig(
            mcc, mnc, language, region, orientation, touchscreen, density,
            keyboard, navigation, inputFlags, grammaticalInflection, screenWidth,
            screenHeight, sdkVersion, minorVersion, screenLayout, uiMode,
            smallestScreenWidthDp, screenWidthDp, screenHeightDp, localeScript,
            localeVariant, screenLayout2, colorMode, unknown,
        )

        if (config.isInvalid) {
            mInvalidConfigs.add(config)
        }

        return config
    }

    /** 解包 2 字节语言/地区码（高位为 1 时是 3 字母压缩码）。 */
    private fun unpackLanguageOrRegion(data: ByteArray, base: Char): String {
        var `in` = data
        // "any" 语言返回空串。
        if (`in`[0].toInt() == 0) {
            return ""
        }

        // 高位置 1：3 字母打包编码。
        if (`in`[0].toInt() and 0x80 != 0) {
            `in` = byteArrayOf(
                (base.code + (`in`[1].toInt() and 0x1F)).toByte(),
                (base.code + ((`in`[1].toInt() and 0xE0) ushr 5) + ((`in`[0].toInt() and 0x03) shl 3)).toByte(),
                (base.code + ((`in`[0].toInt() and 0x7C) ushr 2)).toByte(),
            )
        }

        return String(`in`, StandardCharsets.US_ASCII)
    }

    /** 解析单条目：常规/稀疏/紧凑编码分别处理；返回 (key, value)。 */
    @Throws(AndrolibException::class, IOException::class)
    private fun parseEntry(typeName: String): Pair<Int, ResValue?>? {
        val input = mIn!!
        // ResTable_entry
        val size = input.readUnsignedShort()
        val flags = input.readUnsignedShort()
        var key = input.readInt()

        val isComplex = flags and ENTRY_FLAG_COMPLEX != 0
        val isCompact = flags and ENTRY_FLAG_COMPACT != 0

        if (key == NO_ENTRY && !isCompact) {
            return null
        }

        if (isCompact) {
            mHasCompactEntries = true
        }

        val value: ResValue? = if (isComplex && !isCompact) {
            parseBag(typeName)
        } else if (isCompact) {
            // 紧凑条目：类型在 flags 高 8 位，key 即数据本身，假定尺寸 8 字节。
            val type = (flags ushr 8) and 0xFF
            val v = parseItem(typeName, false, type, key)

            // 紧凑条目的 size 字段编码了 key 索引。
            key = size
            v
        } else {
            parseItem(typeName, false)
        }

        return Pair(key, value)
    }

    /** 解析 map entry（style/array/attr/plurals）。 */
    @Throws(AndrolibException::class, IOException::class)
    private fun parseBag(typeName: String): ResValue? {
        val input = mIn!!
        // ResTable_map_entry
        val parentId = input.readInt()
        val count = input.readInt()

        // 部分应用把 enum/flag 的 ID 资源值存成空 map，替换为占位值。
        if (typeName == "id") {
            return ResCustom.ID
        }

        val parent = ResReference(mPackage!!, ResId.of(parentId))
        var rawItems = arrayOfNulls<ResBag.RawItem>(count)
        var rawItemsCount = 0

        for (i in 0 until count) {
            // ResTable_map
            val name = input.readInt()
            val value = parseItem(typeName, true) as ResItem?

            // #2824 - 重复畸形条目跳过。
            if (value == null) {
                continue
            }

            rawItems[rawItemsCount++] = ResBag.RawItem(name, value)
        }

        @Suppress("UNCHECKED_CAST")
        if (rawItemsCount < rawItems.size) {
            rawItems = rawItems.copyOf(rawItemsCount)
        }

        return ResBag.parse(typeName, parent, rawItems as Array<ResBag.RawItem>)
    }

    /** 解析 Res_value 头并分派。 */
    @Throws(AndrolibException::class, IOException::class)
    private fun parseItem(typeName: String, inBag: Boolean): ResValue? {
        val input = mIn!!
        // Res_value
        val size = input.readUnsignedShort()
        if (size < 8) {
            return null
        }
        input.skipByte() // res0
        val type = input.readUnsignedByte()
        val data = input.readInt()

        return parseItem(typeName, inBag, type, data)
    }

    /** 值语义分派：id 占位、字符串/文件引用、其余走 ResItem.parse。 */
    @Throws(AndrolibException::class)
    private fun parseItem(typeName: String, inBag: Boolean, type: Int, data: Int): ResValue? {
        // ID 资源值要么是布尔 false 要么是引用；false 已不被 XML 允许，替换占位。
        if (typeName == "id" && (data == 0 ||
                (type != ResValue.TYPE_REFERENCE && type != ResValue.TYPE_DYNAMIC_REFERENCE))
        ) {
            return ResCustom.ID
        }

        // 字符串与文件引用特殊处理。
        if (type == ResValue.TYPE_STRING) {
            if (!mValueStringPool.isLoaded) {
                throw AndrolibException("Missing value string pool.")
            }

            val strValue = mValueStringPool.getText(data)

            // 不允许字符串的位置按文件引用处理；
            // 若是无效文件引用，ResFileDecoder 会回退为字符串值。
            if (strValue is String && strValue.isNotEmpty() && !inBag && typeName != "string") {
                return ResFileReference(strValue)
            }

            return ResString(strValue ?: "")
        }

        return ResItem.parse(mPackage!!, type, data)
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseFlagged(parser: ResChunkPullParser) {
        val input = mIn!!
        // ResTable_flagged
        val flagNameIndex = input.readInt()
        val flagNegated = input.readBoolean()
        input.skipBytes(3) // padding

        skipUnreadHeader(parser)

        // 混淆器可能注入假索引滥用 feature flag。
        val flagName = mFlagMap?.get(flagNameIndex)
        if (flagName == null) {
            Log.d(TAG, "Skipping flagged chunk with undeclared feature flag index: 0x%08x", flagNameIndex)
            parser.skipChunk()
            return
        }

        mFlag = FeatureFlag(flagName, flagNegated)

        var inner = ResChunkPullParser(input, parser.dataSize())
        while (nextChunk(inner)) {
            if (inner.chunkType() == ResChunkHeader.RES_TABLE_TYPE_TYPE) {
                parseType(inner)
            } else {
                skipUnexpectedChunk(inner)
            }
        }

        // 清理。
        mFlag = null
    }

    @Throws(IOException::class)
    private fun parseLibrary(parser: ResChunkPullParser) {
        val input = mIn!!
        // ResTable_lib_header
        val count = input.readInt()

        skipUnreadHeader(parser)

        for (i in 0 until count) {
            // ResTable_lib_entry
            val packageId = input.readInt()
            val packageName = input.readUtf16(128)

            if (packageId != 0 && packageName.isNotEmpty()) {
                mTable.addDynamicRefPackage(packageId, packageName)
            }
        }
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseOverlayable(parser: ResChunkPullParser) {
        val input = mIn!!
        // ResTable_overlayable_header
        val name = input.readUtf16(256)
        val actor = input.readUtf16(256)

        skipUnreadHeader(parser)

        // 无名 overlayable 非法，整体跳过。
        if (name.isEmpty()) {
            return
        }

        // 复用同名 overlayable 避免冲突。
        val overlayable = try {
            mPackage!!.getOverlayable(name)
        } catch (ignored: UndefinedResObjectException) {
            mPackage!!.addOverlayable(name, actor)
        }

        var inner = ResChunkPullParser(input, parser.dataSize())
        while (nextChunk(inner)) {
            if (inner.chunkType() != ResChunkHeader.RES_TABLE_OVERLAYABLE_POLICY_TYPE) {
                skipUnexpectedChunk(inner)
                continue
            }

            // ResTable_overlayable_policy_header
            val flags = input.readInt()
            val entryCount = input.readInt()

            skipUnreadHeader(inner)

            var entries = arrayOfNulls<ResId>(entryCount)
            var entriesCount = 0

            for (i in 0 until entryCount) {
                entries[entriesCount++] = ResId.of(input.readInt())
            }

            @Suppress("UNCHECKED_CAST")
            if (entriesCount < entries.size) {
                entries = entries.copyOf(entriesCount)
            }

            overlayable.addPolicy(flags, entries as Array<ResId>)
        }
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun parseStagedAliases(parser: ResChunkPullParser) {
        val input = mIn!!
        // ResTable_staged_alias_header
        val count = input.readInt()

        skipUnreadHeader(parser)

        for (i in 0 until count) {
            // ResTable_staged_alias_entry
            val stagedResId = input.readInt()
            val finalizedResId = input.readInt()

            if (stagedResId != 0 && finalizedResId != 0) {
                mPackage!!.addAlias(ResId.of(stagedResId), ResId.of(finalizedResId))
            }
        }
    }

    @Throws(IOException::class)
    private fun skipUnexpectedChunk(parser: ResChunkPullParser) {
        Log.w(TAG, "Skipping unexpected %s chunk of %s bytes at 0x%08x.",
            parser.chunkName(), parser.chunkSize(), parser.chunkStart())
        parser.skipChunk()
    }

    /** 有些应用谎报头部尺寸：按实际读取量对比后跳过剩余字节。 */
    @Throws(IOException::class)
    private fun skipUnreadHeader(parser: ResChunkPullParser) {
        val bytesRead = (mIn!!.position() - parser.chunkStart()).toInt()
        readExceedingBytes("Chunk header", parser.headerSize(), bytesRead)
    }

    /** 读完声明尺寸的多余字节；存在非零内容时告警并返回，全零返回 null。 */
    @Throws(IOException::class)
    private fun readExceedingBytes(name: String, size: Int, bytesRead: Int): ByteArray? {
        val bytesExceeding = size - bytesRead
        if (bytesExceeding > 0) {
            val buf = mIn!!.readBytes(bytesExceeding)
            for (element in buf) {
                if (element.toInt() != 0) {
                    Log.w(TAG, "%s size: %s bytes, read: %s bytes. Exceeding bytes: %s",
                        name, size, bytesRead, TextUtils.encodeHex(buf))
                    return buf
                }
            }
        }
        return null
    }

    /** 为缺失的条目规格注入 dummy 条目（greedy 模式）。 */
    @Throws(AndrolibException::class)
    private fun injectDummyEntrySpecs() {
        if (mAllowDummyEntrySpecs) {
            val pkg = mPackage!!
            val parent = ResReference(pkg, ResId.NULL)
            val rawItems = emptyArray<ResBag.RawItem>()

            for (resId in mMissingEntrySpecs) {
                val typeSpec: ResTypeSpec = pkg.getTypeSpec(resId.typeId())
                val typeName = typeSpec.name
                val value: ResValue? = when {
                    typeName == "id" -> ResCustom.ID
                    typeName == "string" -> ResString.EMPTY
                    typeSpec.isBagType() -> ResBag.parse(typeName, parent, rawItems)
                    else -> ResPrimitive.NULL
                }

                pkg.addEntrySpec(resId.typeId(), resId.entryId(), ResEntrySpec.DUMMY_PREFIX + resId)
                pkg.addEntry(resId.typeId(), resId.entryId(), value)
            }
        }

        mMissingEntrySpecs.clear()
    }

    companion object {
        private val TAG = BinaryResourceParser::class.java.name

        private const val NO_ENTRY = -1 // 0xFFFFFFFF
        private const val NO_ENTRY_OFFSET16: Int = 0xFFFF

        // ResTable_typeSpec 标志位：
        // 条目公开。
        private const val SPEC_FLAG_PUBLIC = 0x40000000

        // 该资源 ID 未来构建可能变化；置位时 SPEC_PUBLIC 必然也置位。
        private const val SPEC_FLAG_STAGED_API = 0x20000000

        // ResTable_type 标志位：
        // 稀疏编码：每个条目自带 entryId + 偏移，二分查找；O+ 平台。
        private const val TYPE_FLAG_SPARSE = 0x01

        // 16 位偏移编码：真实偏移 = offset * 4u；0xffff 表示 NO_ENTRY。
        private const val TYPE_FLAG_OFFSET16 = 0x02

        // ResTable_entry 标志位：
        // 复合条目（map 数组跟随）。
        private const val ENTRY_FLAG_COMPLEX = 0x0001

        // 公开资源，允许库引用。
        private const val ENTRY_FLAG_PUBLIC = 0x0002

        // 弱资源，可被同名强资源覆盖（仅链接期有意义）。
        private const val ENTRY_FLAG_WEAK = 0x0004

        // 紧凑条目：类型与值直接编码在条目内。
        private const val ENTRY_FLAG_COMPACT = 0x0008

        // 条目依赖读写 feature flags（常规样本不应出现，#3993）。
        private const val ENTRY_FLAG_USES_FEATURE_FLAGS = 0x0010
    }
}
