/**
  * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
  * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
  * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
  * License. You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
  * specific language governing permissions and limitations under the License.
  */

package kafka.message

import java.nio.ByteBuffer

class SeqNumberUniqueProvider extends UniqueProvider {
  val idMap = scala.collection.mutable.Map[Long, Long]()

  def isUnique(key: ByteBuffer): Boolean = {
    val id = extractId(key).getLong
    if(idMap.contains(id)) {
      val expected = idMap.get(id)
      val seqNumber = extractSeqNumber(key).getLong
      val result =  expected == seqNumber
      idMap.put(id, seqNumber + 1)
      result
    } else {
      idMap.put(id, 1)
      true
    }
  }

  def add (key: ByteBuffer): Unit = {
    val id = extractId(key).getLong
    if(idMap.contains(id)) {
      val seqNumber = idMap.get(id).get + 1
      idMap.put(id, seqNumber)
    } else {
      idMap.put(id, 1)
    }

  }

  def extractId(key: ByteBuffer): ByteBuffer = {
    // Get position
    val position = key.getInt

    // Set position
    key.position(position)

    // Slice
    key.slice
  }

  def extractSeqNumber(key: ByteBuffer): ByteBuffer = {
    // Get position
    val position = key.getInt

    // Set position
    key.position(position + 8)

    // Slice
    key.slice
  }
}
