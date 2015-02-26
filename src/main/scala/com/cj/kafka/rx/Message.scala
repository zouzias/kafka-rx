package com.cj.kafka.rx

import KafkaHelper._
import kafka.message.MessageAndMetadata

// alternate version of kafkas MessageAndMetadata class that tracks consumer offsets per message
case class Message[T](
  value: T,
  topic: String,
  partition: Int,
  offset: Long,
  offsets: OffsetMap = Map[TopicPartition, Long](),
  checkpointWith: Checkpoint = { case (x, fn) => fn(x); x } ) {

  val topicPartition = topic -> partition

  def checkpoint(fn: Correction = x => offsets): OffsetMap = checkpointWith(offsets, fn)

  override def equals(other: Any) = {
    other match {
      case message: Message[T] =>
        message.topic == topic &&
        message.partition == partition &&
        message.offset == offset
      case _ => false
    }
  }

  def kafkaMessage: MessageAndMetadata[Array[Byte], Array[Byte]] = {
    val message = new kafka.message.Message(value.asInstanceOf[Array[Byte]])
    val decoder = new kafka.serializer.DefaultDecoder
    MessageAndMetadata(topic, partition, message, offset, decoder, decoder)
  }

}