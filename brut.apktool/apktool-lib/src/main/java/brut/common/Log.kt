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
package brut.common

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 全局日志门面：按 tag 缓存 [Logger] 实例，提供 debug/info/warn/error 四个级别。
 *
 * 使用 [JvmStatic] 暴露静态方法，保证 Java 调用方 `Log.i(TAG, msg)` 形式不变。
 */
object Log {
    /** tag -> Logger 的并发缓存，避免重复创建 Logger。 */
    private val sCache: ConcurrentMap<String, Logger> = ConcurrentHashMap()

    private fun log(level: Level, tag: String, message: String) {
        val logger = sCache.computeIfAbsent(tag) { Logger.getLogger(it) }
        if (logger.isLoggable(level)) {
            logger.log(level, message)
        }
    }

    private fun log(level: Level, tag: String, message: String, vararg args: Any?) {
        val logger = sCache.computeIfAbsent(tag) { Logger.getLogger(it) }
        if (logger.isLoggable(level)) {
            logger.log(level, String.format(message, *args))
        }
    }

    /** 输出 FINE 级别日志。 */
    @JvmStatic
    fun d(tag: String, message: String) = log(Level.FINE, tag, message)

    /** 以 [String.format] 格式化后输出 FINE 级别日志。 */
    @JvmStatic
    fun d(tag: String, message: String, vararg args: Any?) = log(Level.FINE, tag, message, *args)

    /** 输出 INFO 级别日志。 */
    @JvmStatic
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)

    /** 以 [String.format] 格式化后输出 INFO 级别日志。 */
    @JvmStatic
    fun i(tag: String, message: String, vararg args: Any?) = log(Level.INFO, tag, message, *args)

    /** 输出 WARNING 级别日志。 */
    @JvmStatic
    fun w(tag: String, message: String) = log(Level.WARNING, tag, message)

    /** 以 [String.format] 格式化后输出 WARNING 级别日志。 */
    @JvmStatic
    fun w(tag: String, message: String, vararg args: Any?) = log(Level.WARNING, tag, message, *args)

    /** 输出 SEVERE 级别日志。 */
    @JvmStatic
    fun e(tag: String, message: String) = log(Level.SEVERE, tag, message)

    /** 以 [String.format] 格式化后输出 SEVERE 级别日志。 */
    @JvmStatic
    fun e(tag: String, message: String, vararg args: Any?) = log(Level.SEVERE, tag, message, *args)
}
