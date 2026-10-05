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
package brut.androlib

/**
 * apktool 全局配置模型（CLI 参数解析后注入，贯穿解码/构建两条流水线）。
 *
 * 分三段：通用选项（并发/框架目录/共享库）、解码选项（源码/资源/引用解析策略）、
 * 构建选项（aapt2 路径、是否产包、debuggable/网络配置注入等）。
 * 枚举模式以 is*Full/is*None 谓词暴露。
 */
class Config(
    /** apktool 版本号。 */
    val version: String,
) {
    /** 源码（dex）解码范围。 */
    enum class DecodeSources { FULL, ONLY_MAIN_CLASSES, NONE }

    /** 资源解码范围。 */
    enum class DecodeResources { FULL, ONLY_MANIFEST, NONE }

    /** 引用解析策略。 */
    enum class DecodeResolve { DEFAULT, GREEDY, LAZY }

    /** assets 解码范围。 */
    enum class DecodeAssets { FULL, NONE }

    // 通用选项

    /** 并发任务数（默认 CPU 核数，封顶 8）。 */
    var jobs: Int = minOf(Runtime.getRuntime().availableProcessors(), 8)

    /** framework 目录覆盖路径。 */
    var frameworkDirectory: String? = null

    /** framework 版本 tag。 */
    var frameworkTag: String? = null

    /** 共享库名 -> 库文件路径列表。 */
    val libraryFiles: MutableMap<String, Array<String>> = LinkedHashMap()

    /** 强制覆盖已有输出。 */
    var isForced: Boolean = false

    /** 详细日志。 */
    var isVerbose: Boolean = false

    // 解码选项

    private var mDecodeSources: DecodeSources = DecodeSources.ONLY_MAIN_CLASSES

    /** baksmali 保留调试信息。 */
    var isBaksmaliDebugMode: Boolean = true

    private var mDecodeResources: DecodeResources = DecodeResources.FULL

    private var mDecodeResolve: DecodeResolve = DecodeResolve.DEFAULT

    /** 保留损坏资源而非置空。 */
    var isKeepBrokenResources: Boolean = false

    /** 忽略原始（非二进制）属性值。 */
    var isIgnoreRawValues: Boolean = false

    /** 分析模式（只读，不落盘完整工程）。 */
    var isAnalysisMode: Boolean = false

    private var mDecodeAssets: DecodeAssets = DecodeAssets.FULL

    /** 是否全量反汇编所有 dex。 */
    val isDecodeSourcesFull: Boolean
        get() = mDecodeSources == DecodeSources.FULL

    /** 是否完全跳过反汇编。 */
    val isDecodeSourcesNone: Boolean
        get() = mDecodeSources == DecodeSources.NONE

    /** 设置源码解码范围。 */
    fun setDecodeSources(decodeSources: DecodeSources) {
        mDecodeSources = decodeSources
    }

    /** 是否完整解码资源。 */
    val isDecodeResourcesFull: Boolean
        get() = mDecodeResources == DecodeResources.FULL

    /** 是否完全跳过资源解码。 */
    val isDecodeResourcesNone: Boolean
        get() = mDecodeResources == DecodeResources.NONE

    /** 设置资源解码范围。 */
    fun setDecodeResources(decodeResources: DecodeResources) {
        mDecodeResources = decodeResources
    }

    /** 引用解析使用懒模式（缺失即跳过）。 */
    val isDecodeResolveLazy: Boolean
        get() = mDecodeResolve == DecodeResolve.LAZY

    /** 引用解析使用贪婪模式（自动补引用）。 */
    val isDecodeResolveGreedy: Boolean
        get() = mDecodeResolve == DecodeResolve.GREEDY

    /** 设置引用解析策略。 */
    fun setDecodeResolve(decodeResolve: DecodeResolve) {
        mDecodeResolve = decodeResolve
    }

    /** 是否全量解码 assets。 */
    val isDecodeAssetsFull: Boolean
        get() = mDecodeAssets == DecodeAssets.FULL

    /** 是否跳过 assets。 */
    val isDecodeAssetsNone: Boolean
        get() = mDecodeAssets == DecodeAssets.NONE

    /** 设置 assets 解码范围。 */
    fun setDecodeAssets(decodeAssets: DecodeAssets) {
        mDecodeAssets = decodeAssets
    }

    // 构建选项

    /** 只生成目录不产 APK。 */
    var isNoApk: Boolean = false

    /** 跳过 9-patch 压图。 */
    var isNoCrunch: Boolean = false

    /** 原样拷贝原始文件（不重编资源）。 */
    var isCopyOriginal: Boolean = false

    /** 注入 android:debuggable=true。 */
    var isDebuggable: Boolean = false

    /** 注入宽松的网络安全配置。 */
    var isNetSecConf: Boolean = false

    /** aapt2 二进制路径覆盖。 */
    var aaptBinary: String? = null
}
