import java.util.Properties
import java.util.concurrent.TimeUnit
import org.apache.kafka.common.serialization._
import org.apache.kafka.streams._
import org.apache.kafka.streams.kstream._

class RankingByEmailInitializer extends Initializer[Double] {
  override def apply(): Double = 0f
}

class RankingByEmailAggregator extends Aggregator[String, Double, Double] {
  override def apply(aggKey: String, value: Double, aggregate: Double) = {
    aggregate + value
  }
}


object RatingStreamProcessingApp extends App {

  val config = createConfig(args)
  run()

  private def createConfig(args:Array[String]) : Properties = {
    val config: Properties = {
      val p = new Properties()
      val bootstrapServers = if (args.length > 0) args(0) else "localhost:9092"
      p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
      p.put(StreamsConfig.APPLICATION_ID_CONFIG, "ratings-application")
      p.put(StreamsConfig.CLIENT_ID_CONFIG, "ratings-application-client")
      p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass)
      p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass)
      // Records should be flushed every 10 seconds. This is less than the default
      // in order to keep this example interactive.
      p.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10000.asInstanceOf[Object])
      // For illustrative purposes we disable record caches
      p.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0.asInstanceOf[Object])
      p
    }
    config
  }


  private def run() : Unit = {
    val stringSerde = Serdes.String
    val doubleSerde = Serdes.Double
    val rankingSerde = new JSONSerde[Ranking]
    val builder: KStreamBuilder = new KStreamBuilder
    val rankings = builder.stream(stringSerde, rankingSerde, RatingsTopics.RATING_SUBMIT_TOPIC)
//    val wordCounts: KTable[String, Long] = rankings
//        .mapValues[(String,Integer)](ranking => (ranking.email, ranking.score))
//        .groupBy((email, _) => email)
//        .count("Counts")





    val mappedRankings = rankings
      .map[String, Double]((k,v) => new KeyValue[String, Double](k,v.score))

    val wordCounts = mappedRankings.groupByKey().aggregate(
      new RankingByEmailInitializer(),
      new RankingByEmailAggregator(),
      doubleSerde
    )




    //To test this with Console-Consumer, can do something like
    //kafka-console-consumer.bat --zookeeper localhost:2181 --topic rating-output-topic
    // --from-beginning --formatter kafka.tools.DefaultMessageFormatter
    // --property print.key=true
    // --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    // --property value.deserializer=org.apache.kafka.common.serialization.DoubleDeserializer
    wordCounts.to(stringSerde, doubleSerde ,RatingsTopics.RATING_OUTPUT_TOPIC)

    val streams: KafkaStreams = new KafkaStreams(builder, config)
    streams.start()

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      streams.close(10, TimeUnit.SECONDS)
    }))

    ()
  }
}



