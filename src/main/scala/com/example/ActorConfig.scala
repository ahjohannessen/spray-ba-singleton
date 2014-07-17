package com.example

import akka.actor.{Actor, ActorSystem}

object Config {
  def apply(system: ActorSystem) = new Config(system)
}

class Config(system: ActorSystem) {
  val config = system.settings.config.getConfig("spray-basic-authentication")

  def bindAddress = config.getString("bind-address")
  def bindPort = config.getInt("bind-port")
}

trait ActorConfig { this: Actor =>
  val pluginConfig = Config(context.system)
}
