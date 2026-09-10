package com.docpipeline.processing;

public record ReceivedQueueMessage(long messageId, int readCount, QueueMessage payload) {}
