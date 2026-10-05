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

import brut.androlib.exceptions.AndrolibException
import brut.androlib.exceptions.UndefinedResObjectException
import brut.androlib.res.data.FeatureFlag
import brut.androlib.res.table.value.ResValue

/**
 * 资源表中的一个包（framework 包或应用包）。
 *
 * 维护五类注册表：类型规格、类型实例、条目规格、条目、overlayable，以及别名映射；
 * 条目名唯一性由 [mNameRegistry] 强制（重复名会被校正为 APKTOOL_RENAMED_）。
 */
class ResPackage(
    private val mGroup: ResPackageGroup,
    private val mIndex: Int,
) {
    private val mTypeSpecs = HashMap<Int, ResTypeSpec>()
    private val mTypes = HashMap<TypeKey, ResType>()
    private val mEntrySpecs = HashMap<ResId, ResEntrySpec>()
    private val mEntries = HashMap<EntryKey, ResEntry>()
    private val mOverlayables = HashMap<String, ResOverlayable>()
    private val mAliases = HashMap<ResId, ResId>()
    private val mNameRegistry = HashSet<String>()

    /** 所属资源表。 */
    fun getTable(): ResTable = mGroup.getTable()

    /** 所属包组。 */
    fun getGroup(): ResPackageGroup = mGroup

    /** 包 ID（来自组）。 */
    fun getId(): Int = mGroup.getId()

    /** 包名（来自组）。 */
    fun getName(): String = mGroup.getName()

    /** 包在表中的下标。 */
    fun getIndex(): Int = mIndex

    /** 是否已登记该类型规格。 */
    fun hasTypeSpec(typeId: Int): Boolean = mTypeSpecs.containsKey(typeId)

    /** 取类型规格。 */
    @Throws(UndefinedResObjectException::class)
    fun getTypeSpec(typeId: Int): ResTypeSpec =
        mTypeSpecs[typeId] ?: throw UndefinedResObjectException(
            String.format("type spec: pkgId=0x%02x, typeId=0x%02x", getId(), typeId)
        )

    /** 登记新类型规格；重复登记抛异常。 */
    @Throws(AndrolibException::class)
    fun addTypeSpec(typeId: Int, typeName: String): ResTypeSpec {
        var typeSpec = mTypeSpecs[typeId]
        if (typeSpec != null) {
            throw AndrolibException(
                String.format(
                    "Repeated type spec: pkgId=0x%02x, typeId=0x%02x, typeName=%s",
                    getId(), typeId, typeName
                )
            )
        }

        typeSpec = ResTypeSpec(this, typeId, typeName)
        mTypeSpecs[typeId] = typeSpec
        return typeSpec
    }

    /** 类型规格数量。 */
    fun getTypeSpecCount(): Int = mTypeSpecs.size

    /** 全部类型规格。 */
    fun listTypeSpecs(): Collection<ResTypeSpec> = mTypeSpecs.values

    /** 是否存在该类型（默认配置）。 */
    fun hasType(typeId: Int): Boolean = hasType(typeId, ResConfig.DEFAULT)

    /** 是否存在该类型（指定配置）。 */
    fun hasType(typeId: Int, config: ResConfig): Boolean = hasType(typeId, config, null)

    /** 是否存在该类型（指定配置与标志）。 */
    fun hasType(typeId: Int, config: ResConfig, flag: FeatureFlag?): Boolean =
        mTypes.containsKey(TypeKey(typeId, config, flag))

    /** 取类型（默认配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int): ResType = getType(typeId, ResConfig.DEFAULT)

    /** 取类型（指定配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int, config: ResConfig): ResType = getType(typeId, config, null)

    /** 取类型（指定配置与标志）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int, config: ResConfig, flag: FeatureFlag?): ResType {
        val typeKey = TypeKey(typeId, config, flag)
        return mTypes[typeKey] ?: throw UndefinedResObjectException(
            String.format("type: pkgId=0x%02x, typeId=0x%02x, config=%s", getId(), typeId, config)
        )
    }

    /** 添加类型（默认配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun addType(typeId: Int): ResType = addType(typeId, ResConfig.DEFAULT)

    /** 添加类型（指定配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun addType(typeId: Int, config: ResConfig): ResType = addType(typeId, config, null)

    /** 添加类型（指定配置与标志）；重复添加安全返回既有类型。 */
    @Throws(UndefinedResObjectException::class)
    fun addType(typeId: Int, config: ResConfig, flag: FeatureFlag?): ResType {
        val typeKey = TypeKey(typeId, config, flag)
        val type = mTypes[typeKey]
        if (type != null) {
            // 已存在的类型可安全复用。
            return type
        }

        val typeSpec = getTypeSpec(typeId)
        val created = ResType(typeSpec, config, flag)
        mTypes[typeKey] = created
        return created
    }

    /** 类型数量。 */
    fun getTypeCount(): Int = mTypes.size

    /** 全部类型。 */
    fun listTypes(): Collection<ResType> = mTypes.values

    /** 条目（别名解析后的）规格是否存在。 */
    fun hasEntrySpec(typeId: Int, entryId: Int): Boolean {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)
        return mEntrySpecs.containsKey(resId)
    }

    /** 取条目规格（别名自动解析）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntrySpec(typeId: Int, entryId: Int): ResEntrySpec {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)
        return mEntrySpecs[resId] ?: throw UndefinedResObjectException(
            String.format(
                "entry spec: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x",
                getId(), resId.typeId(), resId.entryId()
            )
        )
    }

    /** 登记条目规格（重名强制改名）。 */
    @Throws(AndrolibException::class)
    fun addEntrySpec(typeId: Int, entryId: Int, nameArg: String): ResEntrySpec {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)
        val finalTypeId = resId.typeId()
        val finalEntryId = resId.entryId()

        if (mEntrySpecs.containsKey(resId)) {
            throw AndrolibException(
                String.format(
                    "Repeated entry spec: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x",
                    getId(), finalTypeId, finalEntryId
                )
            )
        }

        val typeSpec = getTypeSpec(finalTypeId)

        // 部分应用条目名被混淆或全表折叠为同一字符串；用注册表强制唯一，命中即改名。
        var name = nameArg
        if (mNameRegistry.contains(typeSpec.name + "/" + name)) {
            name = ""
        }

        val entrySpec = ResEntrySpec(typeSpec, finalEntryId, name)
        mEntrySpecs[resId] = entrySpec

        // 登记名称保证唯一。
        mNameRegistry.add(typeSpec.name + "/" + entrySpec.name)

        return entrySpec
    }

    /** 条目规格数量。 */
    fun getEntrySpecCount(): Int = mEntrySpecs.size

    /** 全部条目规格。 */
    fun listEntrySpecs(): Collection<ResEntrySpec> = mEntrySpecs.values

    /** 条目是否存在（默认配置）。 */
    fun hasEntry(typeId: Int, entryId: Int): Boolean = hasEntry(typeId, entryId, ResConfig.DEFAULT)

    /** 条目是否存在（指定配置）。 */
    fun hasEntry(typeId: Int, entryId: Int, config: ResConfig): Boolean =
        hasEntry(typeId, entryId, config, null)

    /** 条目是否存在（指定配置与标志）。 */
    fun hasEntry(typeId: Int, entryId: Int, config: ResConfig, flag: FeatureFlag?): Boolean {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)

        val typeKey = TypeKey(resId.typeId(), config, flag)
        return mEntries.containsKey(EntryKey(resId, typeKey))
    }

    /** 取条目（默认配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int): ResEntry = getEntry(typeId, entryId, ResConfig.DEFAULT)

    /** 取条目（指定配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int, config: ResConfig): ResEntry =
        getEntry(typeId, entryId, config, null)

    /** 取条目（指定配置与标志）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int, config: ResConfig, flag: FeatureFlag?): ResEntry {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)

        val typeKey = TypeKey(resId.typeId(), config, flag)
        val entryKey = EntryKey(resId, typeKey)
        return mEntries[entryKey] ?: throw UndefinedResObjectException(
            String.format(
                "entry: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x, config=%s",
                getId(), resId.typeId(), resId.entryId(), config
            )
        )
    }

    /** 添加条目（默认配置）。 */
    @Throws(AndrolibException::class)
    fun addEntry(typeId: Int, entryId: Int, value: ResValue?): ResEntry =
        addEntry(typeId, entryId, ResConfig.DEFAULT, value)

    /** 添加条目（指定配置）。 */
    @Throws(AndrolibException::class)
    fun addEntry(typeId: Int, entryId: Int, config: ResConfig, value: ResValue?): ResEntry =
        addEntry(typeId, entryId, config, null, value)

    /** 添加条目（完整参数）；缺失的类型自动补建。 */
    @Throws(AndrolibException::class)
    fun addEntry(
        typeId: Int,
        entryId: Int,
        config: ResConfig,
        flag: FeatureFlag?,
        value: ResValue?,
    ): ResEntry {
        var resId = ResId.of(getId(), typeId, entryId)
        resId = mAliases.getOrDefault(resId, resId)

        val typeKey = TypeKey(resId.typeId(), config, flag)
        val entryKey = EntryKey(resId, typeKey)
        if (mEntries.containsKey(entryKey)) {
            throw AndrolibException(
                String.format(
                    "Repeated entry: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x, config=%s",
                    getId(), resId.typeId(), resId.entryId(), config
                )
            )
        }

        val entrySpec = getEntrySpec(resId.typeId(), resId.entryId())
        var type = mTypes[typeKey]
        if (type == null) {
            // 类型缺失时可安全补建。
            val typeSpec = getTypeSpec(resId.typeId())
            type = ResType(typeSpec, config, flag)
            mTypes[typeKey] = type
        }

        val entry = ResEntry(type, entrySpec, value)
        mEntries[entryKey] = entry
        return entry
    }

    /** 条目数量。 */
    fun getEntryCount(): Int = mEntries.size

    /** 全部条目。 */
    fun listEntries(): Collection<ResEntry> = mEntries.values

    /** overlayable 是否存在。 */
    fun hasOverlayable(name: String): Boolean = mOverlayables.containsKey(name)

    /** 取 overlayable。 */
    @Throws(UndefinedResObjectException::class)
    fun getOverlayable(name: String): ResOverlayable =
        mOverlayables[name] ?: throw UndefinedResObjectException(
            String.format("overlayable: pkgId=0x%02x, name=%s", getId(), name)
        )

    /** 登记 overlayable；重复抛异常。 */
    @Throws(AndrolibException::class)
    fun addOverlayable(name: String, actor: String): ResOverlayable {
        val existing = mOverlayables[name]
        if (existing != null) {
            throw AndrolibException(
                String.format("Repeated overlayable: pkgId=0x%02x, name=%s", getId(), name)
            )
        }

        val overlayable = ResOverlayable(this, name, actor)
        mOverlayables[name] = overlayable
        return overlayable
    }

    /** overlayable 数量。 */
    fun getOverlayableCount(): Int = mOverlayables.size

    /** 全部 overlayable。 */
    fun listOverlayables(): Collection<ResOverlayable> = mOverlayables.values

    /** 是否为别名 ID。 */
    fun isAlias(resId: ResId): Boolean = mAliases.containsKey(resId)

    /** 解析别名到最终 ID。 */
    @Throws(UndefinedResObjectException::class)
    fun resolveAlias(aliasId: ResId): ResId =
        mAliases[aliasId] ?: throw UndefinedResObjectException(
            String.format("alias: pkgId=0x%02x, aliasId=%s", getId(), aliasId)
        )

    /** 登记别名；重复抛异常。 */
    @Throws(AndrolibException::class)
    fun addAlias(aliasId: ResId, finalId: ResId) {
        if (mAliases.containsKey(aliasId)) {
            throw AndrolibException(
                String.format("Repeated alias: pkgId=0x%02x, aliasId=%s", getId(), aliasId)
            )
        }

        mAliases[aliasId] = finalId
    }

    /** 别名数量。 */
    fun getAliasCount(): Int = mAliases.size

    /** 别名映射（只读视图语义，同原实现直接暴露内部表）。 */
    fun getAliases(): Map<ResId, ResId> = mAliases

    override fun toString(): String =
        String.format("ResPackage{id=0x%02x, name=%s}", getId(), getName())

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResPackage
        return mGroup == that.mGroup && mIndex == that.mIndex
    }

    override fun hashCode(): Int = 31 * mGroup.hashCode() + mIndex

    /** 类型索引键：typeId + config + flag。 */
    private class TypeKey(
        val typeId: Int,
        val config: ResConfig,
        val flag: FeatureFlag?,
    ) {
        override fun toString(): String =
            String.format("TypeKey{typeId=0x%02x, config=%s, flag=%s}", typeId, config, flag)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }
            val that = other as TypeKey
            return typeId == that.typeId && config == that.config && flag == that.flag
        }

        override fun hashCode(): Int {
            var result = typeId
            result = 31 * result + config.hashCode()
            result = 31 * result + (flag?.hashCode() ?: 0)
            return result
        }
    }

    /** 条目索引键：resId + typeKey。 */
    private class EntryKey(
        val resId: ResId,
        val typeKey: TypeKey,
    ) {
        override fun toString(): String =
            String.format("EntryKey{resId=%s, typeKey=%s}", resId, typeKey)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }
            val that = other as EntryKey
            return resId == that.resId && typeKey == that.typeKey
        }

        override fun hashCode(): Int = 31 * resId.hashCode() + typeKey.hashCode()
    }
}
