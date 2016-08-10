/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.processor.internals;


import org.apache.kafka.clients.producer.CompletionCallback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.internals.TxnListener;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;
import java.util.concurrent.Future;

class StreamsProducer<K, V> extends KafkaProducer<K, V> {

    StreamsProducer(Map<String, Object> configs,
                    Serializer<K> keySerializer,
                    Serializer<V> valueSerializer,
                    TxnListener listener) {
        super(configs, keySerializer, valueSerializer);
    }

    /**
     * Start a new set of messages to commit. All calls to send through this producer instance
     * are considered to be part of this transaction. We end the transaction once the application
     * calls endTxn.
     *
     * @param callback Invoked when the initialization of the batch completes
     * @return A future to indicate when the operations is complete.
     */
    Future<Void> beginTxn(CompletionCallback<Void> callback) {
        return super.beginTxn(callback);
    }

    /**
     * Commit the last set of produced messages.
     *
     * @param callback Invoked when the commit completes
     * @return A future to indicate when the operations completes.
     */
    Future<Void> endTxn(byte[] metadata, CompletionCallback<Void> callback) {
        return super.endTxn(metadata, callback);
    }

    /**
     * Aborts the current set of messages to commit. The messages that have been
     * successfully produced won't be delivered to consumers that set the commit
     * mode to COMMITTED.
     *
     * @param callback Invoked when the abort operation completes
     * @return A future to indicate when the operation completes.
     */
    Future<Void> abortTxn(CompletionCallback<Void> callback) {
        return super.abortTxn(callback);
    }
}
