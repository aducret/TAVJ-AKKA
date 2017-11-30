package ar.com.itba.actors;

import akka.actor.*;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class SystemMonitor extends AbstractActor {

    static public Props props(Boolean logs) {
        return Props.create(SystemMonitor.class, () -> new SystemMonitor(logs));
    }

    private Cancellable cancellable;
    private Boolean logs;

    public SystemMonitor(Boolean logs) {
        this.logs = logs;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        ActorSystem system = getContext().system();
        cancellable = system.scheduler().schedule(Duration.Zero(),
                Duration.create(60, TimeUnit.SECONDS), getSelf(), "Tick",
                system.dispatcher(), null);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        cancellable.cancel();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetSystemInfo.class, message -> getSystemInfo(message))
                .matchEquals("Tick", message -> tick())
                .build();
    }

    private void getSystemInfo(GetSystemInfo message) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long cores = Runtime.getRuntime().availableProcessors();

        System.out.println("SystemMonitor(getSystemInfo) - Sending stats");
        getSender().tell(new SystemStats(totalMemory, maxMemory, freeMemory, cores), getSelf());
    }

    private void tick() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long cores = Runtime.getRuntime().availableProcessors();

        Document document = new Document()
                .append("time", System.currentTimeMillis())
                .append("totalMemory", totalMemory)
                .append("maxMemory", maxMemory)
                .append("freeMemory", freeMemory)
                .append("cores", cores);

        MongoClient client = new MongoClient();

        client
            .getDatabase("TAVJ-AKKA")
            .getCollection("systemStats")
            .insertOne(document);

        client.close();

        if (logs) {
            System.out.println("SystemMonitor(tick) - Total memory (bytes): " + totalMemory);
            System.out.println("SystemMonitor(tick) - Maximum memory (bytes): " + maxMemory);
            System.out.println("SystemMonitor(tick) - Free memory (bytes): " + freeMemory);
            System.out.println("SystemMonitor(tick) - Available processors (cores): " + cores);
        }
    }

    static public class GetSystemInfo { }

    static public class SystemStats {

        private long totalMemory;
        private long maxMemory;
        private long freeMemory;
        private long cores;

        public SystemStats(long totalMemory, long maxMemory, long freeMemory, long cores) {
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.freeMemory = freeMemory;
            this.cores = cores;
        }

        public long getCores() {
            return cores;
        }

        public long getFreeMemory() {
            return freeMemory;
        }

        public long getMaxMemory() {
            return maxMemory;
        }

        public long getTotalMemory() {
            return totalMemory;
        }
    }

}
