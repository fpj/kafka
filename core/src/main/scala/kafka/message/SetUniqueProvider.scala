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

class SetUniqueProvider extends UniqueProvider {
  val idSet: collection.mutable.Set[ByteBuffer]

  /**
    * Check that the id is unique. The input parameter is the message key.
    *
    * @param key
    * @return
    */
  def isUnique(key: ByteBuffer): Boolean = {
    val id = extractId(key)
    idSet.contains(id)
  }

  /**
    * Adds id to the set. The parameter is the key in the message,
    * so we need to extract the id.
    *
    * @param key
    */
  def add (key: ByteBuffer): Unit = {
    // TODO: We can't simply add to the set, we need to garbage
    // collect old ids too. If we move forward wit this implementation
    // then such a mechanism needs to be implemented.
    idSet.add(extractId(key))
  }

  /**
    * Extracts the id from the key parameter and assumes that the first integer
    * out of the ByteBuffer determines the position of the id in the buffer.
    *
    * @param key
    * @return
    */
  def extractId(key: ByteBuffer): ByteBuffer = {
    val idPos = key.getInt()
    if(idPos > key.capacity)
      throw new InvalidMessageException("Invalid key for deduplication.")
    key.position(idPos)
    key.slice
  }
}
