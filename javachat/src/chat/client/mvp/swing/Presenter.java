package chat.client.mvp.swing;

public interface Presenter {

  void setView(View view);

  void stop();

  boolean openConnection(String username);

  void closeConnection();

  void parseMessage(String message);

  void sendPrvMsg(String message, String userList);

}
