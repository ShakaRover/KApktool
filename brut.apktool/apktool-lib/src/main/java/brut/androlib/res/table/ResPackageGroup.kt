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
import brut.androlib.res.data.FeatureFlag
import java.util.NoSuchElementException

/**
 * 同一包 ID 的多个包实例分组（应用包 + 若干补丁子包）。
 *
 * 查找顺序：基包优先、子包随后；[listEntrySpecs] / [listEntries] 提供跨包懒连接视图。
 */
class ResPackageGroup(
    private val mTable: ResTable,
    private val mId: Int,
    private val mName: String,
) {
    private val mPackages = ArrayList<ResPackage>()

    init {
        mPackages.add(ResPackage(this, 0))
    }

    /** 所属资源表。 */
    fun getTable(): ResTable = mTable

    /** 包 ID。 */
    fun getId(): Int = mId

    /** 包名。 */
    fun getName(): String = mName

    /** 包数量（含基包）。 */
    fun getPackageCount(): Int = mPackages.size

    /** 全部包。 */
    fun listPackages(): List<ResPackage> = mPackages

    /** 基包（第 0 个）。 */
    fun getBasePackage(): ResPackage = mPackages[0]

    /** 子包视图（不含基包；返回原列表的活视图）。 */
    fun listSubPackages(): List<ResPackage> = mPackages.subList(1, mPackages.size)

    /** 追加一个新子包。 */
    fun addSubPackage(): ResPackage {
        val pkg = ResPackage(this, mPackages.size)
        mPackages.add(pkg)
        return pkg
    }

    /** 任意成员包登记了该类型规格即为 true。 */
    fun hasTypeSpec(typeId: Int): Boolean {
        for (pkg in mPackages) {
            if (pkg.hasTypeSpec(typeId)) {
                return true
            }
        }
        return false
    }

    /** 跨包查找类型规格。 */
    @Throws(UndefinedResObjectException::class)
    fun getTypeSpec(typeId: Int): ResTypeSpec {
        for (pkg in mPackages) {
            try {
                return pkg.getTypeSpec(typeId)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        throw UndefinedResObjectException(
            String.format("type spec: pkgId=0x%02x, typeId=0x%02x", mId, typeId)
        )
    }

    /** 是否存在该类型（默认配置）。 */
    fun hasType(typeId: Int): Boolean = hasType(typeId, ResConfig.DEFAULT)

    /** 是否存在该类型（指定配置）。 */
    fun hasType(typeId: Int, config: ResConfig): Boolean = hasType(typeId, config, null)

    /** 是否存在该类型（完整参数）。 */
    fun hasType(typeId: Int, config: ResConfig, flag: FeatureFlag?): Boolean {
        for (pkg in mPackages) {
            if (pkg.hasType(typeId, config, flag)) {
                return true
            }
        }
        return false
    }

    /** 取类型（默认配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int): ResType = getType(typeId, ResConfig.DEFAULT)

    /** 取类型（指定配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int, config: ResConfig): ResType = getType(typeId, config, null)

    /** 跨包查找类型（完整参数）。 */
    @Throws(UndefinedResObjectException::class)
    fun getType(typeId: Int, config: ResConfig, flag: FeatureFlag?): ResType {
        for (pkg in mPackages) {
            try {
                return pkg.getType(typeId, config, flag)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        throw UndefinedResObjectException(
            String.format(
                "type: pkgId=0x%02x, typeId=0x%02x, config=%s, flag=%s", mId, typeId, config, flag
            )
        )
    }

    /** 是否存在该条目规格。 */
    fun hasEntrySpec(typeId: Int, entryId: Int): Boolean {
        for (pkg in mPackages) {
            if (pkg.hasEntrySpec(typeId, entryId)) {
                return true
            }
        }
        return false
    }

    /** 跨包查找条目规格。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntrySpec(typeId: Int, entryId: Int): ResEntrySpec {
        for (pkg in mPackages) {
            try {
                return pkg.getEntrySpec(typeId, entryId)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        throw UndefinedResObjectException(
            String.format("entry spec: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x", mId, typeId, entryId)
        )
    }

    /** 跨包懒遍历全部条目规格。 */
    fun listEntrySpecs(): Iterable<ResEntrySpec> = ChainedIterable { it.listEntrySpecs() }

    /** 是否存在该条目（默认配置）。 */
    fun hasEntry(typeId: Int, entryId: Int): Boolean = hasEntry(typeId, entryId, ResConfig.DEFAULT)

    /** 是否存在该条目（指定配置）。 */
    fun hasEntry(typeId: Int, entryId: Int, config: ResConfig): Boolean =
        hasEntry(typeId, entryId, config, null)

    /** 是否存在该条目（完整参数）。 */
    fun hasEntry(typeId: Int, entryId: Int, config: ResConfig, flag: FeatureFlag?): Boolean {
        for (pkg in mPackages) {
            if (pkg.hasEntry(typeId, entryId, config, flag)) {
                return true
            }
        }
        return false
    }

    /** 取条目（默认配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int): ResEntry = getEntry(typeId, entryId, ResConfig.DEFAULT)

    /** 取条目（指定配置）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int, config: ResConfig): ResEntry =
        getEntry(typeId, entryId, config, null)

    /** 跨包查找条目（完整参数）。 */
    @Throws(UndefinedResObjectException::class)
    fun getEntry(typeId: Int, entryId: Int, config: ResConfig, flag: FeatureFlag?): ResEntry {
        for (pkg in mPackages) {
            try {
                return pkg.getEntry(typeId, entryId, config, flag)
            } catch (ignored: UndefinedResObjectException) {
            }
        }
        throw UndefinedResObjectException(
            String.format(
                "entry: pkgId=0x%02x, typeId=0x%02x, entryId=0x%04x, config=%s, flag=%s",
                mId, typeId, entryId, config, flag
            )
        )
    }

    /** 跨包懒遍历全部条目。 */
    fun listEntries(): Iterable<ResEntry> = ChainedIterable { it.listEntries() }

    override fun toString(): String =
        String.format("ResPackageGroup{id=0x%02x, name=%s}", mId, mName)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResPackageGroup
        return mId == that.mId && mName == that.mName
    }

    override fun hashCode(): Int = 31 * mId + mName.hashCode()

    /** 按成员包顺序懒串联子集合的 Iterable。 */
    private inner class ChainedIterable<T>(private val selector: (ResPackage) -> Collection<T>) :
        Iterable<T> {
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            private var current: Iterator<T> = emptyList<T>().iterator()
            private var index = 0

            override fun hasNext(): Boolean {
                while (!current.hasNext() && index < mPackages.size) {
                    current = selector(mPackages[index++]).iterator()
                }
                return current.hasNext()
            }

            override fun next(): T {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                return current.next()
            }
        }
    }
}
