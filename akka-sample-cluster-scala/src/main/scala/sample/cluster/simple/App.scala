package sample.cluster.simple

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import com.typesafe.config.ConfigFactory


//Avvia App 25251 e poi App 0
//Console: UN ALTRO = è stato registrato un nuovo nodo nel receptionist
//         PROVA DA (indirizzo) = messaggio ricevuto da un attore appena io (nodo) mi collego al cluster (fatto per tutti gli attori registrati nel receptionist)
//                  se 127.0.0.1:porta senza path aggiuntivo allora è il nodo stesso che si è inviato un messaggio

object App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      // Create an actor that handles cluster domain events
      context.spawn(ClusterListener(), "ClusterListener")

      Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
    val ports =
      if (args.isEmpty)
        Seq(25251, 25252, 0)
      else
        args.toSeq.map(_.toInt)
    ports.foreach(startup)
  }

  def startup(port: Int): Unit = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      """).withFallback(ConfigFactory.load())

    // Create an Akka system
    ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }

}
