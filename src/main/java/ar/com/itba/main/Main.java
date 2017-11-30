package ar.com.itba.main;

import static akka.pattern.PatternsCS.ask;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import ar.com.itba.actors.GameRoom;
import ar.com.itba.actors.GameRoomManager;
import ar.com.itba.actors.SystemMonitor;

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends AllDirectives {

    public static final String HOST = "localhost";
    public static final int PORT = 8080;
    public static final int TIME_OUT = 10000;

    private static ActorSystem actorSystem;
    private static ActorRef gameRoomManager;
    private static ActorRef systemMonitor;

    private static Boolean systemLogs = false;

    public static void main( String[] args ) throws IOException {
        // Disable Mongo logs
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);

        actorSystem = ActorSystem.create("TAVJ-AKKA");
        gameRoomManager = actorSystem.actorOf(GameRoomManager.props(), "GameRoomManager");
        systemMonitor = actorSystem.actorOf(SystemMonitor.props(systemLogs), "SytemMonitor");

        ActorSystem system = ActorSystem.create("Main");
        Http http = Http.get(system);
        Main main = new Main();
        ActorMaterializer materializer = ActorMaterializer.create(system);
        Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource = http
                .bind(ConnectHttp.toHost(HOST, PORT), materializer);
        Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = main.createRoutes().flow(system, materializer);
        CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow, ConnectHttp.toHost(HOST, PORT), materializer);


        System.out.println(String.format("Server online at http://%s:%d/\nPress RETURN to stop...", HOST, PORT));
        System.in.read();
        binding
            .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
            .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }

    private Route createRoutes() {
        return route(
            pathSingleSlash(() -> complete("Welcome!")),
            createGameRoomRoute(),
            getSystemDataRoute(),
            getGameRoomAddressRoute(),
            joinGameRoomRoute(),
            leaveGameRoomRoute(),
            deleteGameRoomRoute()
        );
    }

    private Route createGameRoomRoute() {
        return path(PathMatchers.segment("create").slash("gameRoom"), () -> parameter("userId", userId -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(createGameRoomRoute) - Processing create GameRoom with userId: " + userId);
            try {
                Object message = ask(gameRoomManager, new GameRoomManager.CreateGameRoom(userId), TIME_OUT).toCompletableFuture().get();
                if (message instanceof GameRoomManager.GameRoomCreated) {
                    GameRoomManager.GameRoomCreated gameRoomCreate = (GameRoomManager.GameRoomCreated) message;
                    String gameRoomId = gameRoomCreate.getGameRoomId();
                    System.out.println("Main(createGameRoomRoute) - GameRoomCreated(id: " + gameRoomId + ", ownerId: " + userId + ") received");
                    return complete(StatusCodes.OK, "Create GameRoom(id: " + gameRoomId + ") owner userId " + userId);
                } else if (message instanceof GameRoomManager.UserIsAlreadyInGame) {
                    GameRoomManager.UserIsAlreadyInGame userIsAlreadyInGame = (GameRoomManager.UserIsAlreadyInGame) message;
                    String gameRoomId = userIsAlreadyInGame.getGameRoomId();
                    System.out.println("Main(createGameRoomRoute) - User(id: " + userId + ") is already in GameRoom(id: " + gameRoomId + ")");
                    return complete(StatusCodes.CONFLICT, "User(id: " + userId + ") already join to GameRoom(id: "+ gameRoomId +").");
                } else {
                    System.out.println("Main(createGameRoomRoute) - Unknown message received");
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        }));
    }

    private Route deleteGameRoomRoute() {
        return path(PathMatchers.segment("delete").slash("gameRoom"), () -> parameter("id", gameRoomId -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(deleteGameRoomRoute) - Processing delete GameRoom(id: " + gameRoomId + ")");
            try {
                Object message = ask(gameRoomManager, new GameRoomManager.DeleteGameRoom(gameRoomId), TIME_OUT).toCompletableFuture().get();
                if (message instanceof GameRoomManager.GameRoomDeleted) {
                    System.out.println("Main(deleteGameRoomRoute) - GameRoomDeleted(id: " + gameRoomId + ") received");
                    return complete(StatusCodes.OK, "GameRoom(id: " + gameRoomId + ") deleted.");
                } else if (message instanceof GameRoomManager.UnknownGameRoom) {
                    System.out.println("Main(deleteGameRoomRoute) - TryingToDeleteUnknownGameRoom(id: " + gameRoomId + ") received");
                    return complete(StatusCodes.CONFLICT, "Trying to delete unknown GameRoom(id: " + gameRoomId + ").");
                } else {
                    System.out.println("Main(deleteGameRoomRoute) - Unknown message received");
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        }));
    }

    private Route joinGameRoomRoute() {
        return path(PathMatchers.segment("join").slash("gameRoom"), () -> parameter("id", gameRoomId -> parameter("userId", userId -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(joinGameRoomRoute) - Processing join User(id: " + userId + ") to GameRoom(id: " + gameRoomId + ")");
            try {
                Object message = ask(gameRoomManager, new GameRoomManager.JoinGameRoom(gameRoomId, userId), TIME_OUT).toCompletableFuture().get();
                if (message instanceof GameRoom.JoinGameRoomSuccessfully) {
                    System.out.println("Main(joinGameRoomRoute) - JoinGameRoomSuccessfully(gameRoomId: " + gameRoomId + ", userId: "+ userId + ") received");
                    return complete(StatusCodes.OK, "User(id: " + userId + ") joined GameRoom(id: " + gameRoomId + ").");
                } else if (message instanceof GameRoom.UserAlreadyJoinToGameRoom) {
                    System.out.println("Main(joinGameRoomRoute) - UserAlreadyJoinToGameRoom(id: " + gameRoomId + ", userId: " + userId + ") received");
                    return complete(StatusCodes.CONFLICT, "User(id: " + userId + ") already join to GameRoom(id: "+ gameRoomId +").");
                } else if (message instanceof GameRoomManager.UserIsAlreadyInGame) {
                    System.out.println("Main(joinGameRoomRoute) - UserAlreadyJoinToGameRoom(id: " + gameRoomId + ", userId: " + userId + ") received");
                    return complete(StatusCodes.CONFLICT, "User(id: " + userId + ") already join to GameRoom(id: "+ gameRoomId +").");
                } else if (message instanceof GameRoomManager.UnknownGameRoom) {
                    System.out.println("Main(joinGameRoomRoute) - UnknownGameRoom(id: " + gameRoomId + ") received");
                    return complete(StatusCodes.CONFLICT, "Invalid GameRoom(id: " + gameRoomId+ ").");
                } else if (message instanceof GameRoom.GameRoomIsFull) {
                    System.out.println("Main(joinGameRoomRoute) - GameRoomIsFull(id: " + gameRoomId + ") received");
                    return complete(StatusCodes.CONFLICT, "GameRoom(id: " + gameRoomId+ ") is full.");
                } else {
                    System.out.println("Main(joinGameRoomRoute) - Unknown message received");
                    System.out.println(message);
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch(Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        })));
    }

    private Route leaveGameRoomRoute() {
        return path(PathMatchers.segment("leave").slash("gameRoom"), () -> parameter("id", gameRoomId -> parameter("userId", userId -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(leaveGameRoomRoute) - Processing User(id: " + userId + ") leaving GameRoom(id: " + gameRoomId + ")");
            try {
                Object message = ask(gameRoomManager, new GameRoomManager.LeaveGameRoom(gameRoomId, userId), TIME_OUT).toCompletableFuture().get();
                if (message instanceof GameRoom.LeaveGameRoomSuccessfully) {
                    System.out.println("Main(leaveGameRoomRoute) - LeaveGameRoomSuccessfully(gameRoomId: " + gameRoomId + ", userId: "+ userId + ") received");
                    return complete(StatusCodes.OK, "User(id: " + userId + ") left GameRoom(id: " + gameRoomId + ").");
                } else if (message instanceof GameRoom.UserIsNotInGameRoom) {
                    System.out.println("Main(leaveGameRoomRoute) - UserIsNotInGameRoom(gameRoomId: " + gameRoomId + ", userId: "+ userId + ") received");
                    return complete(StatusCodes.CONFLICT, "The User(id: " + userId + ") is not in the GameRoom(id: " + gameRoomId + ").");
                } else if (message instanceof GameRoomManager.UnknownGameRoom) {
                    System.out.println("Main(leaveGameRoomRoute) - UnknownGameRoom(id: " + gameRoomId + ") received");
                    return complete(StatusCodes.CONFLICT, "Invalid GameRoom(id: " + gameRoomId+ ").");
                } else {
                    System.out.println("Main(leaveGameRoomRoute) - Unknown message received");
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        })));
    }

    private Route getSystemDataRoute() {
        return path(PathMatchers.segment("get").slash("system"), () -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(getSystemDataRoute) - Processing get system stats");
            try {
                Object message = ask(systemMonitor, new SystemMonitor.GetSystemInfo(), TIME_OUT).toCompletableFuture().get();
                if (message instanceof SystemMonitor.SystemStats) {
                    SystemMonitor.SystemStats stats = (SystemMonitor.SystemStats) message;
                    return complete(StatusCodes.OK, "System stats \n\nTotal memory (bytes): " + stats.getTotalMemory() + "\nMax memory (bytes): " + stats.getMaxMemory() + "\nFree memory (bytes): " + stats.getFreeMemory() + "\nCores: " + stats.getCores());
                } else {
                    System.out.println("Main(getSystemDataRoute) - Unknown message received");
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        });
    }

    private Route getGameRoomAddressRoute() {
        return path(PathMatchers.segment("list").slash("gameRoom"), () -> {
            System.out.println("---------------------------------------------");
            System.out.println("Main(getGameRoomAddressRoute) - Processing get system stats");
            try {
                Object message = ask(gameRoomManager, new GameRoomManager.GetGameRoomList(), TIME_OUT).toCompletableFuture().get();
                if (message instanceof GameRoomManager.GameRoomList) {
                    GameRoomManager.GameRoomList gameRoomList = (GameRoomManager.GameRoomList) message;
                    String text = "Game rooms \n\n";
                    for (String id: gameRoomList.getList()) {
                        text += "GameRoom(id: " + id + ")\n";
                    }
                    return complete(StatusCodes.OK, text);
                } else {
                    System.out.println("Main(getGameRoomAddressRoute) - Unknown message received");
                    return complete(StatusCodes.CONFLICT, "Unknown message received. Operation failed.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                return complete(StatusCodes.CONFLICT, "Exception received. Operation failed.");
            }
        });
    }

}
