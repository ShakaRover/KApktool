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
package brut.yaml

import java.io.IOException

/**
 * 可序列化到 apktool YAML 方言的对象契约。
 *
 * [onEntry] 在解析时逐条接收键值；[serialize] 写出自身。
 */
interface YamlSerializable {
    /** 解析回调：收到一条键值记录。 */
    @Throws(IOException::class)
    fun onEntry(parser: YamlPullParser)

    /** 把自身写入序列化器。 */
    @Throws(IOException::class)
    fun serialize(serial: YamlSerializer)
}
