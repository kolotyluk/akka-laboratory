package net.kolotyluk.akka.net.kolotyluk.scala


import com.typesafe.config.{Config, ConfigFactory}

import collection.JavaConverters._

trait Configuration {
  def config = Configuration.config
}

object Configuration {
  private val config = ConfigFactory.load()

  override def toString: String = {
    val entryStrings = config.entrySet.asScala.map {
      entry => entry.getKey + " = " + entry.getValue.unwrapped.toString
    }
    entryStrings.toSeq.sorted.mkString("\n", "\n", "\n")
  }
}
