package ar.com.itba.actors;

import akka.actor.*;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class SystemMonitor extends AbstractActor {

    static public Props props() {
        return Props.create(SystemMonitor.class, () -> new SystemMonitor());
    }

    private Cancellable cancellable;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        System.out.println("preStart");
        ActorSystem system = getContext().system();
        cancellable = system.scheduler().schedule(Duration.Zero(),
                Duration.create(1, TimeUnit.SECONDS), getSelf(), "Tick",
                system.dispatcher(), null);
    }

    @Override
    public void postStop() throws Exception {
        System.out.println("postStop");
        super.postStop();
        cancellable.cancel();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("Tick", message -> tick())
                .build();
    }

    private void tick() {
        System.out.println("SystemMonitor(tick) - ");
    }

    public static class Tick { }

}
