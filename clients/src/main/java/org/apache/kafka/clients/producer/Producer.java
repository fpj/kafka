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
package org.apache.kafka.clients.producer;

import java.io.Closeable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;


/**
 * The interface for the {@link KafkaProducer}
 * @see KafkaProducer
 * @see MockProducer
 */
public interface Producer<K, V> extends Closeable {

    /**
     * A producer id is an identifier that Kafka assigns to enable the
     * producer to:
     *      - Deduplicate messages sent to a topic partition
     *      - Identify and recover incomplete commits
     *
     * A commit corresponds to a set of messages that need to
     * be produced atomically, all or none.
     *
     * The first time an application starts a producer with a given commit
     * id, the producer needs to set it up so that Kafka can deduplicate
     * and recover commits. The setupPid call only needs to be invoked once
     * to obtain a new producer id. Once the application obtains a producer
     * id, it should user the recoverPid call to complete an incomplete
     * commit.
     *
     * @param cb A completion callback for this setup call.
     * @return A future for the result of setting up a producer id, the
     *         value returned is a newly allocated producer id.
     */
    public Future<String> newPid(CompletionCallback<Void> cb);

    /**
     * Recover the state of a given Producer ID the producer identifier
     * enables the producer instance to recover all necessary state to
     * both deduplicate messages and finish incomplete commits. We can
     * have an incomplete commit in the case the application has crashed
     * and needs to restart.
     *
     * In the case the application is interested in the only-once
     * guarantee and it has pending commits, it must call this
     * method with the appropriate producer ID before resuming
     * its regular execution.
     *
     * @param cb A callback to notify the application that recovery
     *           has finished.
     * @return A future that eventually returns the result of recovery.
     */
    public Future<Void> recoverPid(String pid, CompletionCallback<Void> cb);

    /**
     * Send the given record asynchronously and return a future which will eventually contain the response information.
     * 
     * @param record The record to send
     * @return A future which will eventually contain the response information
     */
    public Future<RecordMetadata> send(ProducerRecord<K, V> record);

    /**
     * Send a record and invoke the given callback when the record has been acknowledged by the server
     */
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback);

    /**
     * Start a new set of messages to commit. All calls to send through this producer instance
     * are considered to be part of this commit set. We close the commit set once the application
     * calls endCommit.
     *
     * @param callback Invoked when the initialization of the batch completes
     * @return A future to indicate when the operations is complete.
     */
    public Future<Void> beginCommit(CompletionCallback<Void> callback);

    /**
     * Commit the last set of produced messages.
     *
     * @param callback Invoked when the commit completes
     * @return A future to indicate when the operations completes.
     */
    public Future<Void> endCommit(CompletionCallback<Void> callback);

    /**
     * Aborts the current set of messages to commit. The messages that have been
     * successfully produced won't be delivered to consumers that set the commit
     * mode to COMMITTED.
     *
     * @param callback Invoked when the abort operation completes
     * @return A future to indicate when the operation completes.
     */
    public Future<Void> abortCommit(CompletionCallback<Void> callback);

    /**
     * Flush any accumulated records from the producer. Blocks until all sends are complete.
     */
    public void flush();

    /**
     * Get a list of partitions for the given topic for custom partition assignment. The partition metadata will change
     * over time so this list should not be cached.
     */
    public List<PartitionInfo> partitionsFor(String topic);

    /**
     * Return a map of metrics maintained by the producer
     */
    public Map<MetricName, ? extends Metric> metrics();

    /**
     * Close this producer
     */
    public void close();

    /**
     * Tries to close the producer cleanly within the specified timeout. If the close does not complete within the
     * timeout, fail any pending send requests and force close the producer.
     */
    public void close(long timeout, TimeUnit unit);

}
