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
package brut.androlib.res.xml

import brut.androlib.exceptions.AndrolibException
import brut.androlib.res.table.ResEntry
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/** 可写入 values 目录下 XML 的资源值契约。 */
interface ValuesXmlSerializable {
    /** 序列化自身到 values XML。 */
    @Throws(AndrolibException::class, IOException::class)
    fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry)
}
