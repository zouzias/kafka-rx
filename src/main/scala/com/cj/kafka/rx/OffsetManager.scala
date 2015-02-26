package com.cj.kafka.rx

import KafkaHelper._
import kafka.message.MessageAndMetadata

class OffsetManager(commit: Commit = (_,_,_) => Map[TopicPartition, Long]()) {

  // Offset Manager keeps track of offsets per partition for a particular kafka stream
  private var currentOffsets = Map[TopicPartition, Long]()
  def getOffsets: OffsetMap = currentOffsets

  def check(message: MessageAndMetadata[Array[Byte], Array[Byte]]): Option[Message[Array[Byte]]] = {
    // updates internal offset state & determines whether the message is stale due to replay
    currentOffsets.get(message.topic -> message.partition) match {
      case None => manageMessage(message)
      case Some(priorOffset) =>
        if (message.offset > priorOffset) manageMessage(message)
        else None
    }
  }

  def rebalanceOffsets(externalOffsets: OffsetMap, internalOffsets: OffsetMap): OffsetMap = {
    // kafka rebalancing can cause skewed views of partition ownership
    // by reconciling our offsets with another view, we can determine which we have ownership over
    currentOffsets = currentOffsets.filter { case (topicPartition, offset) =>
      val externalOffset = externalOffsets.getOrElse(topicPartition, -1L)
      offset >= externalOffset
    }
    internalOffsets filter { case (topicPartition, offset) =>
      currentOffsets.contains(topicPartition)
    } map { case (topicPartition, offset) =>
      topicPartition -> (offset + 1)
    }
  }

  def balancedCommit(offsets: OffsetMap, correction: Correction): OffsetMap = {
    commit(offsets, correction, rebalanceOffsets)
  }

  private def manageMessage(msg: MessageAndMetadata[Array[Byte], Array[Byte]]): Some[Message[Array[Byte]]] = {
    val message = KafkaHelper.copyMessage(msg, currentOffsets, balancedCommit)
    currentOffsets += message.topicPartition -> message.offset
    Some(message)
  }
  
}
