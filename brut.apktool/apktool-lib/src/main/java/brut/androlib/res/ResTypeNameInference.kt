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
package brut.androlib.res

import brut.androlib.res.table.ResEntry
import brut.androlib.res.table.ResPackage
import brut.androlib.res.table.ResTable
import brut.androlib.res.table.ResTypeSpec
import brut.androlib.res.table.value.ResArray
import brut.androlib.res.table.value.ResAttribute
import brut.androlib.res.table.value.ResFileReference
import brut.androlib.res.table.value.ResPlural
import brut.androlib.res.table.value.ResPrimitive
import brut.androlib.res.table.value.ResString
import brut.androlib.res.table.value.ResStyle
import brut.common.Log

/**
 * 为混淆/非法的资源类型名推断合法名（对应 `--infer-res-type-names`，默认关闭）。
 *
 * 背景：部分加固工具把 `resources.arsc` 里的类型名改成任意字符串（如 `ulfnf0000`）。
 * apktool 默认把这类类型名兜底成 `invalid%02X`——工程自洽，但 `res/invalid01/`
 * 不是 aapt2 认可的目录名，回编必失败。
 *
 * 开启本功能后，会依据该类型下条目的**值**推断一个合法类型名，并统一改名，
 * 因此目录名、`res/values/public.xml` 与所有引用会一起变化，工程保持自洽。
 *
 * 注意：这只是让工程可回编的**启发式**手段。资源类型 ID 在回编时由 aapt2 重新分配，
 * 因此推断名不必与原始名一致；但若应用在运行时按类型名查询资源
 * （`getIdentifier(name, type, pkg)`），推断名不同会导致查询失败。
 */
internal object ResTypeNameInference {
    private val TAG = ResTypeNameInference::class.java.name

    /** 对整张表执行推断；返回被改名的类型数。 */
    fun apply(table: ResTable): Int {
        var renamed = 0
        for (group in table.listPackageGroups()) {
            for (pkg in group.listPackages()) {
                renamed += applyToPackage(pkg)
            }
        }
        return renamed
    }

    private fun applyToPackage(pkg: ResPackage): Int {
        val specs = pkg.listTypeSpecs()
        val pending = specs.filter { it.needsNameInference }.sortedBy { it.getId() }
        if (pending.isEmpty()) {
            return 0
        }

        // 已被合法类型占用的名字不可复用。
        val used = HashSet<String>()
        for (spec in specs) {
            if (!spec.needsNameInference) {
                used.add(spec.name)
            }
        }

        val entriesByType = pkg.listEntries().groupBy { it.getType().getId() }
        var renamed = 0
        for (spec in pending) {
            val entries = entriesByType[spec.getId()].orEmpty()

            // 先按值推断；推断不出或名字已被占用时，退化为第一个未被占用的标准名。
            var candidate = inferFromEntries(entries)
            if (candidate == null || candidate in used) {
                candidate = ResTypeSpec.DIRECTORY_TYPE_NAMES.firstOrNull { it !in used }
            }
            if (candidate == null) {
                Log.w(TAG, "Could not infer a name for resource type id=0x%02x, keeping %s",
                    spec.getId(), spec.name)
                continue
            }

            Log.w(TAG, "Inferred resource type name for id=0x%02x: %s -> %s",
                spec.getId(), spec.name, candidate)
            spec.rename(candidate)
            used.add(candidate)
            renamed++
        }
        return renamed
    }

    /** 依据该类型下条目的值推断类型名；无法判断时返回 null。 */
    private fun inferFromEntries(entries: List<ResEntry>): String? {
        val values = entries.mapNotNull { it.value }
        if (values.isEmpty()) {
            return null
        }

        // 袋类型：由解析出的具体袋类决定（解析阶段按 ENTRY_FLAG_COMPLEX 走袋分支）。
        values.firstOrNull { it is ResStyle }?.let { return "style" }
        values.firstOrNull { it is ResPlural }?.let { return "plurals" }
        values.firstOrNull { it is ResArray }?.let { return "array" }
        values.firstOrNull { it is ResAttribute }?.let { return "attr" }

        // 全部是字符串。
        if (values.all { it is ResString }) {
            return "string"
        }

        // 原始值：所有条目的格式一致时才判定。
        val formats = values.filterIsInstance<ResPrimitive>().map { it.getFormat() }.toSet()
        if (formats.size == 1) {
            primitiveTypeName(formats.first())?.let { return it }
        }

        // 文件引用：所有条目都是文件且扩展名归入同一类别时才判定。
        val files = values.filterIsInstance<ResFileReference>()
        if (files.size == values.size) {
            val categories = files.map { fileTypeCategory(it.path) }.toSet()
            if (categories.size == 1) {
                categories.first()?.let { return it }
            }
        }

        return null
    }

    private fun primitiveTypeName(format: String?): String? = when (format) {
        "integer" -> "integer"
        "color" -> "color"
        "boolean" -> "bool"
        "dimension" -> "dimen"
        "fraction" -> "fraction"
        "string" -> "string"
        else -> null
    }

    /**
     * 按文件扩展名归入资源类别（返回对应类型名）。
     *
     * 同一类型下常混有不同扩展名的文件（如 raw 下的 json/mp3/mp4/wav），
     * 因此按类别而非精确扩展名归类。
     */
    private fun fileTypeCategory(path: String): String? = when (extensionOf(path)) {
        // 位图（含 .9.png）。
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> "drawable"
        // XML 无法仅凭扩展名区分 anim/layout/xml，统一落到 xml
        // （回编时类型 ID 由 aapt2 重排，工程内部保持一致即可）。
        "xml" -> "xml"
        "ttf", "otf", "ttc" -> "font"
        "" -> null
        // 其余（mp3/mp4/wav/json/...）按 raw 处理。
        else -> "raw"
    }

    /** 取扩展名（小写）；无扩展名或点出现在路径分隔符之前时返回空串。 */
    private fun extensionOf(path: String): String {
        val dot = path.lastIndexOf('.')
        val separator = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (dot <= separator) "" else path.substring(dot + 1).lowercase()
    }
}
