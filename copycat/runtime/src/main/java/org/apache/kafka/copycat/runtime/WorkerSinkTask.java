/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package org.apache.kafka.copycat.runtime;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.copycat.data.SchemaAndValue;
import org.apache.kafka.copycat.errors.CopycatException;
import org.apache.kafka.copycat.sink.SinkRecord;
import org.apache.kafka.copycat.sink.SinkTask;
import org.apache.kafka.copycat.storage.Converter;
import org.apache.kafka.copycat.util.ConnectorTaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * WorkerTask that uses a SinkTask to export data from Kafka.
 */
class WorkerSinkTask implements WorkerTask {
    private static final Logger log = LoggerFactory.getLogger(WorkerSinkTask.class);

    private final ConnectorTaskId id;
    private final SinkTask task;
    private final WorkerConfig workerConfig;
    private final Time time;
    private final Converter keyConverter;
    private final Converter valueConverter;
    private WorkerSinkTaskThread workThread;
    private KafkaConsumer<byte[], byte[]> consumer;
    private WorkerSinkTaskContext context;
    private Map<TopicPartition, OffsetAndMetadata> lastCommittedOffsets;

    public WorkerSinkTask(ConnectorTaskId id, SinkTask task, WorkerConfig workerConfig,
                          Converter keyConverter, Converter valueConverter, Time time) {
        this.id = id;
        this.task = task;
        this.workerConfig = workerConfig;
        this.keyConverter = keyConverter;
        this.valueConverter = valueConverter;
        this.time = time;
    }

    @Override
    public void start(Properties props) {
        consumer = createConsumer();
        context = new WorkerSinkTaskContext(consumer);

        // Ensure we're in the group so that if start() wants to rewind offsets, it will have an assignment of partitions
        // to work with. Any rewinding will be handled immediately when polling starts.
        String topicsStr = props.getProperty(SinkTask.TOPICS_CONFIG);
        if (topicsStr == null || topicsStr.isEmpty())
            throw new CopycatException("Sink tasks require a list of topics.");
        String[] topics = topicsStr.split(",");
        log.debug("Task {} subscribing to topics {}", id, topics);
        consumer.subscribe(Arrays.asList(topics), new HandleRebalance());
        consumer.poll(0);

        task.initialize(context);
        task.start(props);

        workThread = createWorkerThread();
        workThread.start();
    }

    @Override
    public void stop() {
        // Offset commit is handled upon exit in work thread
        if (workThread != null)
            workThread.startGracefulShutdown();
        consumer.wakeup();
    }

    @Override
    public boolean awaitStop(long timeoutMs) {
        boolean success = true;
        if (workThread != null) {
            try {
                success = workThread.awaitShutdown(timeoutMs, TimeUnit.MILLISECONDS);
                if (!success)
                    workThread.forceShutdown();
            } catch (InterruptedException e) {
                success = false;
            }
        }
        task.stop();
        return success;
    }

    @Override
    public void close() {
        // FIXME Kafka needs to add a timeout parameter here for us to properly obey the timeout
        // passed in
        if (consumer != null)
            consumer.close();
    }

    /** Poll for new messages with the given timeout. Should only be invoked by the worker thread. */
    public void poll(long timeoutMs) {
        try {
            rewind();
            long retryTimeout = context.timeout();
            if (retryTimeout > 0) {
                timeoutMs = Math.min(timeoutMs, retryTimeout);
                context.timeout(-1L);
            }
            log.trace("{} polling consumer with timeout {} ms", id, timeoutMs);
            ConsumerRecords<byte[], byte[]> msgs = consumer.poll(timeoutMs);
            log.trace("{} polling returned {} messages", id, msgs.count());
            deliverMessages(msgs);
        } catch (WakeupException we) {
            log.trace("{} consumer woken up", id);
        }
    }

    /**
     * Starts an offset commit by flushing outstanding messages from the task and then starting
     * the write commit. This should only be invoked by the WorkerSinkTaskThread.
     **/
    public void commitOffsets(boolean sync, final int seqno) {
        log.info("{} Committing offsets", this);
        final HashMap<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        for (TopicPartition tp : consumer.assignment()) {
            long pos = consumer.position(tp);
            offsets.put(tp, new OffsetAndMetadata(pos));
            log.trace("{} committing {} offset {}", id, tp, pos);
        }

        try {
            task.flush(offsets);
        } catch (Throwable t) {
            log.error("Commit of {} offsets failed due to exception while flushing: {}", this, t);
            log.error("Rewinding offsets to last committed offsets");
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : lastCommittedOffsets.entrySet()) {
                log.debug("{} Rewinding topic partition {} to offset {}", id, entry.getKey(), entry.getValue().offset());
                consumer.seek(entry.getKey(), entry.getValue().offset());
            }
            workThread.onCommitCompleted(t, seqno);
            return;
        }

        if (sync) {
            try {
                consumer.commitSync(offsets);
                lastCommittedOffsets = offsets;
            } catch (KafkaException e) {
                workThread.onCommitCompleted(e, seqno);
            }
        } else {
            OffsetCommitCallback cb = new OffsetCommitCallback() {
                @Override
                public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception error) {
                    lastCommittedOffsets = offsets;
                    workThread.onCommitCompleted(error, seqno);
                }
            };
            consumer.commitAsync(offsets, cb);
        }
    }

    public Time time() {
        return time;
    }

    public WorkerConfig workerConfig() {
        return workerConfig;
    }

    private KafkaConsumer<byte[], byte[]> createConsumer() {
        // Include any unknown worker configs so consumer configs can be set globally on the worker
        // and through to the task
        Properties props = workerConfig.unusedProperties();
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "copycat-" + id.connector());
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                Utils.join(workerConfig.getList(WorkerConfig.BOOTSTRAP_SERVERS_CONFIG), ","));
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        KafkaConsumer<byte[], byte[]> newConsumer;
        try {
            newConsumer = new KafkaConsumer<>(props);
        } catch (Throwable t) {
            throw new CopycatException("Failed to create consumer", t);
        }

        return newConsumer;
    }

    private WorkerSinkTaskThread createWorkerThread() {
        return new WorkerSinkTaskThread(this, "WorkerSinkTask-" + id, time, workerConfig);
    }

    private void deliverMessages(ConsumerRecords<byte[], byte[]> msgs) {
        // Finally, deliver this batch to the sink
        if (msgs.count() > 0) {
            List<SinkRecord> records = new ArrayList<>();
            for (ConsumerRecord<byte[], byte[]> msg : msgs) {
                log.trace("Consuming message with key {}, value {}", msg.key(), msg.value());
                SchemaAndValue keyAndSchema = keyConverter.toCopycatData(msg.topic(), msg.key());
                SchemaAndValue valueAndSchema = valueConverter.toCopycatData(msg.topic(), msg.value());
                records.add(
                        new SinkRecord(msg.topic(), msg.partition(),
                                keyAndSchema.schema(), keyAndSchema.value(),
                                valueAndSchema.schema(), valueAndSchema.value(),
                                msg.offset())
                );
            }

            try {
                task.put(records);
            } catch (CopycatException e) {
                log.error("Exception from SinkTask {}: ", id, e);
            } catch (Throwable t) {
                log.error("Unexpected exception from SinkTask {}: ", id, t);
            }
        }
    }

    private void rewind() {
        Map<TopicPartition, Long> offsets = context.offsets();
        if (offsets.isEmpty()) {
            return;
        }
        for (TopicPartition tp: offsets.keySet()) {
            Long offset = offsets.get(tp);
            if (offset != null) {
                log.trace("Rewind {} to offset {}.", tp, offset);
                consumer.seek(tp, offset);
            }
        }
        context.clearOffsets();
    }

    private class HandleRebalance implements ConsumerRebalanceListener {
        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            lastCommittedOffsets = new HashMap<>();
            for (TopicPartition tp : partitions) {
                long pos = consumer.position(tp);
                lastCommittedOffsets.put(tp, new OffsetAndMetadata(pos));
                log.trace("{} assigned topic partition {} with offset {}", id, tp, pos);
            }
            // Instead of invoking the assignment callback on initialization, we guarantee the consumer is ready upon
            // task start. Since this callback gets invoked during that initial setup before we've started the task, we
            // need to guard against invoking the user's callback method during that period.
            if (workThread != null)
                task.onPartitionsAssigned(partitions);
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            task.onPartitionsRevoked(partitions);
            commitOffsets(true, -1);
        }
    }
}
