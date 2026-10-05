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

/**
 * brut 系列模块的公共受检异常基类。
 *
 * 上层模块（目录、资源、androlib）的所有受检异常都继承自它。
 * 保留全部四个构造函数以维持 Java 端的二进制与源码兼容性。
 */
open class BrutException : Exception {
    /** 无信息构造。 */
    constructor() : super()

    /** 仅携带错误信息。 */
    constructor(message: String?) : super(message)

    /** 仅携带底层原因。 */
    constructor(cause: Throwable?) : super(cause)

    /** 携带错误信息与底层原因。 */
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
