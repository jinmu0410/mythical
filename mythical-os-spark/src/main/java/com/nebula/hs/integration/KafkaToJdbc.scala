package com.nebula.hs.integration

import com.google.gson.Gson
import com.hs.common.DataType
import com.hs.config.KafkaToJdbcJobConfig
import com.hs.utils.CommonFunction.sendToKafka
import com.hs.utils.JdbcWriter
import com.hs.utils.Transformer.fieldNameTransform
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.{col, from_json, get_json_object}
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.util.LongAccumulator
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.mutable

object KafkaToJdbc {

  val checkpointPathFormat = "/delta/events/_checkpoints/kafka2jdbc/%s"

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      throw new IllegalArgumentException("任务配置信息异常!")
    }

    val params = args(0)

    println("任务配置信息：" + params)

    implicit val formats: DefaultFormats.type = DefaultFormats
    val kafkaToJdbcJobConfig = JsonMethods.parse(params).extract[KafkaToJdbcJobConfig]

    val checkpointPath = checkpointPathFormat.format(kafkaToJdbcJobConfig.taskID)

    val sparkConfMap = Map(
      "spark.app.name" -> "kafkaToJdbc-%s".format(kafkaToJdbcJobConfig.taskID),
      "spark.sql.streaming.checkpointLocation" -> checkpointPath,
      "spark.sql.legacy.parquet.int96RebaseModeInWrite" -> "CORRECTED",
      "spark.sql.parquet.datetimeRebaseModeInWrite" -> "CORRECTED",
      "spark.sql.legacy.timeParserPolicy" -> "CORRECTED"
    )

    /**
     * readStream 配置信息
     */
    val readStreamOptions = mutable.Map(
      "kafka.bootstrap.servers" -> kafkaToJdbcJobConfig.kafkaServers,
      "spark.sql.streaming.forceDeleteTempCheckpointLocation" -> "true",
      "minOffsetsPerTrigger" -> "1",
      "maxTriggerDelay" -> "1m",
      "maxOffsetsPerTrigger" -> kafkaToJdbcJobConfig.batchSize,
      "startingOffsets" -> kafkaToJdbcJobConfig.consumeMode,
      "subscribe" -> kafkaToJdbcJobConfig.topicName,
      "failOnDataLoss" -> "false"
    )

    val customParams = kafkaToJdbcJobConfig.customParams
    if (customParams != null && customParams.nonEmpty) {
      customParams.split(",").foreach(e => {
        readStreamOptions.put(e.split(":")(0), e.split(":")(1))
      })
    }

    /**
     * write jdbc 配置
     */
    val jdbcWriteOptions = Map(
      "url" -> kafkaToJdbcJobConfig.jdbcUrl,
      "user" -> kafkaToJdbcJobConfig.username,
      "password" -> kafkaToJdbcJobConfig.password,
      "dbtable" -> kafkaToJdbcJobConfig.targetTable,
      "batchsize" -> kafkaToJdbcJobConfig.batchSize
    )

    /**
     * 构建schema
     */
    val schema = StructType(
      kafkaToJdbcJobConfig.sourceColumns.split(",").map(x =>
        StructField(x.split(":")(0), DataType.getType(x.split(":")(1)), nullable = true))
    )

    val spark = SparkSession
      .builder()
      //.master("local")
      .config(new SparkConf().setAll(sparkConfMap))
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val dataFrame = spark.readStream
      .format("kafka")
      .options(readStreamOptions)
      .load()
    var tranDF: DataFrame = null
    if (kafkaToJdbcJobConfig.jsonPath != null && kafkaToJdbcJobConfig.jsonPath.nonEmpty) {
      val jsonPath = "$." + kafkaToJdbcJobConfig.jsonPath
      tranDF = dataFrame.selectExpr("CAST(value AS " + kafkaToJdbcJobConfig.valueType + ")")
        .withColumn("msg", from_json(get_json_object(col("value"), jsonPath), schema))
        .selectExpr("msg.*")
    } else {
      tranDF = dataFrame.selectExpr("CAST(value AS " + kafkaToJdbcJobConfig.valueType + ")")
        .withColumn("msg", from_json(col("value"), schema))
        .selectExpr("msg.*")
    }

    tranDF.printSchema()

    tranDF.createOrReplaceTempView("tmpTable_" + kafkaToJdbcJobConfig.taskID)
    val finalDF = spark.sql(fieldNameTransform(kafkaToJdbcJobConfig.columnMap, kafkaToJdbcJobConfig.taskID))

    //用于统计实时数据量
    val accumulator: LongAccumulator = spark.sparkContext.longAccumulator("dataCount")

    val query = finalDF.writeStream
      .option("checkpointLocation", checkpointPath)
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        //todo batchId暂时先不用
        //        batchDF.show()
        val writer = new JdbcWriter(batchDF, kafkaToJdbcJobConfig.writeMode, jdbcWriteOptions)
        writer.write(kafkaToJdbcJobConfig.mergeKeys)

        //将每个batch的数据条数用累加器accumulator，累加
        if (kafkaToJdbcJobConfig.DataCountKafkaServers.nonEmpty && kafkaToJdbcJobConfig.DataCountKafkaServers != null
          && kafkaToJdbcJobConfig.DataCountTopicName.nonEmpty && kafkaToJdbcJobConfig.DataCountTopicName != null
        ) {
          accumulator.add(batchDF.count())
          /* 每个批次的发送时间 */
          val syncTime: String = new SimpleDateFormat("yyyy-MM-dd").format(new Date)
          val messageMap = new java.util.HashMap[String, Object]()
          messageMap.put("id", kafkaToJdbcJobConfig.taskID)
          messageMap.put("dataNum", accumulator.value) //.toString
          messageMap.put("apiDataTypeEnum", "CHANNEL_TYPE_REAL_2")
          messageMap.put("syncTime", syncTime)
          val messageJson: String = new Gson().toJson(messageMap)
          sendToKafka(messageJson, kafkaToJdbcJobConfig.DataCountKafkaServers, kafkaToJdbcJobConfig.DataCountTopicName)
        }
      }
      .start()

    query.awaitTermination()
  }
}
