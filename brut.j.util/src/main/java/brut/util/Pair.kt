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
package brut.util

/**
 * 不可变二元组，用作 Map 组合键等场景。
 *
 * 保留 Java 风格的 `getLeft()` / `getRight()`（由属性自动生成），
 * 并通过自定义 [toString] 维持与旧 Java 实现一致的 `(left,right)` 输出格式。
 */
data class Pair<L, R> private constructor(
    val left: L,
    val right: R,
) {
    override fun toString(): String = "(" + left + "," + right + ")"

    companion object {
        /** 工厂方法：创建一对值。 */
        @JvmStatic
        fun <L, R> of(left: L, right: R): Pair<L, R> = Pair(left, right)
    }
}
