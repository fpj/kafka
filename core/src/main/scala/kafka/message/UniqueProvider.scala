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

/**
  * This trait assumes that keys are only added and it leaves gargabe collection
  * to the implementation. For example, Bloom Filters will need to have a rotation
  * to discard old filters, and set collections will need to keep a FIFO list
  * for removing old identifiers.
  */
trait UniqueProvider {
  def isUnique(key: ByteBuffer): Boolean
  def add (key: ByteBuffer)
  def extractId(key: ByteBuffer): ByteBuffer
}
