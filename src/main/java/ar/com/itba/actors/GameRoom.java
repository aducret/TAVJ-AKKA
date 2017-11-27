package ar.com.itba.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;

import java.util.HashSet;
import java.util.Set;

public class GameRoom extends AbstractActor {

    private final String gameRoomId;
    private final String ownerId;
    private final Set<String> users = new HashSet<>();

    static public Props props(String gameRoomId, String ownerId) {
        return Props.create(GameRoom.class, () -> new GameRoom(gameRoomId, ownerId));
    }

    public GameRoom(String gameRoomId, String ownerId) {
        this.gameRoomId = gameRoomId;
        this.ownerId = ownerId;
        users.add(ownerId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JoinGameRoom.class, message -> joinGameRoom(message))
                .match(LeaveGameRoom.class, message -> leaveGameRoom(message))
                .build();
    }

    private void joinGameRoom(JoinGameRoom message) {
        String userId = message.userId;
        if (users.contains(userId)) {
            System.out.println("GameRoom(joinGameRoom) - User(id: " +  userId + ") already join to GameRoom(" + gameRoomId + ")");
            getSender().tell(new UserAlreadyJoinToGameRoom(), getSelf());
        } else {
            System.out.println("GameRoom(joinGameRoom) - User(id: " +  userId + ") join to GameRoom(" + gameRoomId + ")");
            users.add(userId);
            getSender().tell(new JoinGameRoomSuccessfully(), getSelf());
        }
    }

    private void leaveGameRoom(LeaveGameRoom message) {
        String userId = message.userId;
        if (users.contains(userId)) {
            System.out.println("GameRoom(leaveGameRoom) - User(id: " +  userId + ") left GameRoom(" + gameRoomId + ")");
            users.remove(userId);
            getSender().tell(new LeaveGameRoomSuccessfully(), getSelf());
        } else {
            System.out.println("GameRoom(leaveGameRoom) - User(id: " +  userId + ") is not in the GameRoom(" + gameRoomId + ")");
            getSender().tell(new UserIsNotInGameRoom(), getSelf());
        }
    }

    public static class JoinGameRoom {

        private String userId;

        public JoinGameRoom(String userId) {
            this.userId = userId;
        }
    }

    static public class JoinGameRoomSuccessfully { }

    static public class UserAlreadyJoinToGameRoom { }

    public static class LeaveGameRoom {

        private String userId;

        public LeaveGameRoom(String userId) {
            this.userId = userId;
        }
    }

    static public class LeaveGameRoomSuccessfully { }

    static public class UserIsNotInGameRoom { }

}
