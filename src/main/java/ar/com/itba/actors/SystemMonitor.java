package ar.com.itba.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class SystemMonitor extends AbstractActor {

    static public Props props() {
        return Props.create(SystemMonitor.class, () -> new SystemMonitor());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Foo.class, message -> {
            System.out.println("SystemMonitor");
        }).build();
    }

    public static class Foo {}

}
