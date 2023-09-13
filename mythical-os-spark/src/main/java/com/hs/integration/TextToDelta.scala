package com.hs.integration

import com.hs.common.SparkCommon
import com.hs.config.TextToDeltaJobConfig
import com.hs.utils.DeltaMergeUtil.orderExpr
import com.hs.utils.{FrameWriter, Supplement, Transformer}
import org.apache.spark.sql.SparkSession
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods


object TextToDelta {
  val jobType = "Text2Delta"

  def main(args: Array[String]): Unit = {

    if (args.length == 0) {
      throw new IllegalArgumentException("任务配置信息异常！")
    }

    val params = args(0)
    println("任务配置信息：" + params)

    implicit val formats: DefaultFormats.type = DefaultFormats
    val jobConf = JsonMethods.parse(params).extract[TextToDeltaJobConfig]

    /**
     * spark session 配置信息
     */
    val sparkConfig = SparkCommon.sparkConfig
    sparkConfig.setIfMissing("spark.app.name", s"$jobType-${jobConf.taskID}")

    /**
     * Delta SparkSession
     */
    val spark: SparkSession = SparkCommon.loadSession(sparkConfig)

    /**
     * spark read text options
     */
    val readerOptions: Map[String, String] = Map(
      "wholetext" -> jobConf.wholetext,
      "lineSep" -> jobConf.lineSep,
      "encoding" -> jobConf.encoding,
    )

    var df = spark.read.options(readerOptions).text(jobConf.filePath)

    df.show(truncate = false)


    df.createOrReplaceTempView("tmpTable_" + jobConf.taskID)
    var sinkDf = spark.sql(Transformer.fieldNameTransform(jobConf.columnMap, jobConf.taskID))

    /**
     * 系统字段填充
     */
    sinkDf = Supplement.systemFieldComplete(sinkDf)

    /**
     * 分区字段处理
     */
    sinkDf = Supplement.partitionTrans(sinkDf, jobConf.zoneType, jobConf.zoneFieldCode, jobConf.zoneTargetFieldCode, jobConf.zoneTypeUnit)

    /**
     * 敏感字段加密处理
     */
    sinkDf = Supplement.encryptTrans(sinkDf, jobConf.encrypt)

    // 入湖
    val targetTablePath = jobConf.tablePathFormat.format(jobConf.targetDatabase, jobConf.targetTable)
    val writer = new FrameWriter(sinkDf, jobConf.writeMode.toLowerCase, Option(null))
    // 2022-11-25: 应用端传递了 sortColumns
    writer.write(targetTablePath, Option(jobConf.mergeKeys), Option(orderExpr(jobConf.sortColumns)))

    /**
     * 接入数据量统计
     */
    Supplement.dataCount(jobConf.taskID, "text", sinkDf.count(), jobConf.dataCountKafkaServers, jobConf.dataCountTopicName, jobConf.targetDatabase)
  }
}
