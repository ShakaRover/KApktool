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

/**
 * 资源值基类：集中定义 ResTable_value 的类型码常量（与 Android 平台定义一致）。
 */
abstract class ResValue {
    companion object {
        // 值不含数据。
        const val TYPE_NULL = 0x00
        // data 为资源 ID 引用。
        const val TYPE_REFERENCE = 0x01
        // data 为属性资源 ID（当前主题样式中的 attr，而非资源条目）。
        const val TYPE_ATTRIBUTE = 0x02
        // string 字段持有字符串；data 非零时为字符串池下标，assetCookie 标记来源。
        const val TYPE_STRING = 0x03
        // data 为 IEEE 754 单精度浮点位模式。
        const val TYPE_FLOAT = 0x04
        // data 为复数编码的尺寸值（dimension）。
        const val TYPE_DIMENSION = 0x05
        // data 为复数编码的占比值（fraction）。
        const val TYPE_FRACTION = 0x06
        // 动态资源表引用，需先解析再按 TYPE_REFERENCE 使用。
        const val TYPE_DYNAMIC_REFERENCE = 0x07
        // 动态属性引用，需先解析再按 TYPE_ATTRIBUTE 使用。
        const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
        // 普通整数起始类型码。
        const val TYPE_FIRST_INT = 0x10
        // 十进制整数。
        const val TYPE_INT_DEC = 0x10
        // 十六进制整数（0x）。
        const val TYPE_INT_HEX = 0x11
        // 布尔（0/1）。
        const val TYPE_INT_BOOLEAN = 0x12
        // 颜色常量起始类型码。
        const val TYPE_FIRST_COLOR_INT = 0x1C
        // #AARRGGBB。
        const val TYPE_INT_COLOR_ARGB8 = 0x1C
        // #RRGGBB。
        const val TYPE_INT_COLOR_RGB8 = 0x1D
        // #ARGB。
        const val TYPE_INT_COLOR_ARGB4 = 0x1E
        // #RGB。
        const val TYPE_INT_COLOR_RGB4 = 0x1F
        // 颜色常量结束类型码。
        const val TYPE_LAST_COLOR_INT = 0x1F
        // 普通整数结束类型码。
        const val TYPE_LAST_INT = 0x1F

        // TYPE_NULL 数据：未指定。
        const val DATA_NULL_UNDEFINED = 0
        // TYPE_NULL 数据：显式为 null。
        const val DATA_NULL_EMPTY = 1
    }
}
