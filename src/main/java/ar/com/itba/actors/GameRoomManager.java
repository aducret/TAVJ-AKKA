package ar.com.itba.actors;

import akka.actor.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GameRoomManager extends AbstractActor {

    private long currentId = 0;
    private Map<String, ActorRef> gameRooms = new HashMap<>();
    private Map<String, String> usersInGame = new HashMap<>();

    static public Props props() {
        return Props.create(GameRoomManager.class, () -> new GameRoomManager());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateGameRoom.class, message -> createGameRoom(message))
                .match(DeleteGameRoom.class, message -> deleteGameRoom(message))
                .match(JoinGameRoom.class, message -> joinGameRoom(message))
                .match(LeaveGameRoom.class, message -> leaveGameRoom(message))
                .match(JoinGameRoomSuccessfully.class, message -> joinGameRoomSuccessfully(message))
                .match(LeaveGameRoomSuccessfully.class, message -> leaveGameRoomSuccessfully(message))
                .build();
    }

    private void createGameRoom(CreateGameRoom message) {
        String ownerId = message.userId;
        if (usersInGame.containsKey(ownerId)) {
            String gameRoomId = usersInGame.get(ownerId);
            System.out.println("GameRoomManager(createGameRoom) - User(id: " + ownerId + ") is already in a GameRoom(id: " + gameRoomId + ")");
            getSender().tell(new UserIsAlreadyInGame(gameRoomId), getSelf());
            return;
        }
        String gameRoomId = String.valueOf(++currentId);
        ActorRef gameRoom = getContext().actorOf(GameRoom.props(gameRoomId, ownerId), "GameRoom" + gameRoomId + "_Owner" + ownerId);
        System.out.println("GameRoomManager(createGameRoom) - Created GameRoom(id: " + gameRoomId + ") owner user(id: " + ownerId + ")");
        gameRooms.put(gameRoomId, gameRoom);
        usersInGame.put(ownerId, gameRoomId);
        getSender().tell(new GameRoomCreated(gameRoomId), getSelf());
    }

    private void deleteGameRoom(DeleteGameRoom message) {
        String gameRoomId = message.gameRoomId;
        if (gameRooms.containsKey(gameRoomId)) {
            ActorRef gameRoom = gameRooms.get(gameRoomId);
            gameRoom.tell(PoisonPill.getInstance(), getSelf());
            System.out.println("GameRoomManager(deleteGameRoom) - Deleted GameRoom(id: " + gameRoomId + ")");
            gameRooms.remove(gameRoomId);
            for (Map.Entry<String, String> entry : usersInGame.entrySet()) {
                if (entry.getValue().equals(message.gameRoomId)) {
                    usersInGame.remove(entry.getKey());
                }
            }
            getSender().tell(new GameRoomDeleted(), getSelf());
        } else {
            System.out.println("GameRoomManager(deleteGameRoom) - Trying to delete to an unknown game room");
            getSender().tell(new UnknownGameRoom(), getSelf());
        }
    }

    private void joinGameRoom(JoinGameRoom message) {
        String gameRoomId = message.gameRoomId;
        String userId = message.userId;
        if (usersInGame.containsKey(userId)) {
            System.out.println("GameRoomManager(joinGameRoom) - User(id: " + userId + ") is already in GameRoom(id: " + gameRoomId + ")");
            getSender().tell(new UserIsAlreadyInGame(gameRoomId), getSelf());
            return;
        }

        if (gameRooms.containsKey(gameRoomId)) {
            ActorRef gameRoom = gameRooms.get(gameRoomId);
            System.out.println("GameRoomManager(joinGameRoom) - Joining to GameRoom(id: "+ gameRoomId + ") with User(id: " + userId + ")");
            gameRoom.tell(new GameRoom.JoinGameRoom(userId, getSender()), getSelf());
        } else {
            System.out.println("GameRoomManager(joinGameRoom) - Trying to join to an unknown GameRoom(id: " + gameRoomId + ")");
            getSender().tell(new UnknownGameRoom(), getSelf());
        }
    }

    private void leaveGameRoom(LeaveGameRoom message) {
        String gameRoomId = message.gameRoomId;
        String userId = message.userId;
        if (gameRooms.containsKey(gameRoomId)) {
            ActorRef gameRoom = gameRooms.get(gameRoomId);
            System.out.println("GameRoomManager(leaveGameRoom) - User(id: " + userId + ") leaving GameRoom(id: "+ gameRoomId + ")");
            gameRoom.tell(new GameRoom.LeaveGameRoom(userId, getSender()), getSelf());
        } else {
            System.out.println("GameRoomManager(leaveGameRoom) - Trying to leave an unknown GameRoom(id: " + gameRoomId + ")");
            getSender().tell(new UnknownGameRoom(), getSelf());
        }
    }

    private void joinGameRoomSuccessfully(JoinGameRoomSuccessfully message) {
        String userId = message.userId;
        String gameRoomId = message.gameRoomId;
        usersInGame.put(userId, gameRoomId);
        System.out.println("GameRoomManager(joinGameRoomSuccessfully) - User(id: " + userId + ") joined successfully GameRoom(id: " + gameRoomId + ")");
    }

    private void leaveGameRoomSuccessfully(LeaveGameRoomSuccessfully message) {
        String userId = message.userId;
        String gameRoomId = message.gameRoomId;
        usersInGame.remove(message.userId);
        System.out.println("GameRoomManager(joinGameRoomSuccessfully) - User(id: " + userId + ") left successfully GameRoom(id: " + gameRoomId + ")");
    }

    static public class CreateGameRoom {

        // GameRoom owner
        private final String userId;

        public CreateGameRoom(String userId) {
            this.userId = userId;
        }
    }

    static public class DeleteGameRoom {

        private final String gameRoomId;

        public DeleteGameRoom(String gameRoomId) {
            this.gameRoomId = gameRoomId;
        }
    }

    static public class GameRoomCreated {

        private final String gameRoomId;

        public GameRoomCreated(String gameRoomId) {
            this.gameRoomId = gameRoomId;
        }

        public String getGameRoomId() {
            return gameRoomId;
        }
    }

    static public class GameRoomDeleted { }

    static public class UnknownGameRoom { }

    static public class JoinGameRoom {

        private final String gameRoomId;
        private final String userId;

        public JoinGameRoom(String gameRoomId, String userId) {
            this.gameRoomId = gameRoomId;
            this.userId = userId;
        }
    }

    static public class UserIsAlreadyInGame {

        private String gameRoomId;

        public UserIsAlreadyInGame(String gameRoomId) {
            this.gameRoomId = gameRoomId;
        }

        public String getGameRoomId() {
            return gameRoomId;
        }
    }

    static public class JoinGameRoomSuccessfully {

        private String userId;
        private String gameRoomId;

        public JoinGameRoomSuccessfully(String userId, String gameRoomId) {
            this.userId = userId;
            this.gameRoomId = gameRoomId;
        }
    }

    static public class LeaveGameRoomSuccessfully {
        private String userId;
        private String gameRoomId;

        public LeaveGameRoomSuccessfully(String userId, String gameRoomId) {
            this.userId = userId;
            this.gameRoomId = gameRoomId;
        }
    }

    static public class LeaveGameRoom {

        private final String gameRoomId;
        private final String userId;

        public LeaveGameRoom(String gameRoomId, String userId) {
            this.gameRoomId = gameRoomId;
            this.userId = userId;
        }
    }

}
