#!/bin/bash
set -e

BROKER="localhost:9092"
PARTITIONS=6
REPLICATION=1

echo "Creating Kafka topics..."

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic workflow.trigger.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic workflow.action.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic workflow.action.retries \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic job.intake.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic agent.pipeline.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

kafka-topics --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic agent.human.checkpoints \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

echo "Topics created. Listing:"
kafka-topics --list --bootstrap-server $BROKER