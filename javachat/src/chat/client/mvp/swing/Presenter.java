package chat.client.mvp.swing;

public interface Presenter {

  void setView(ViewSwing view);

  void stop();

  boolean openConnection(String username);

  void closeConnection();

  void sendMsg(String message);

  void sendPrvMsg(String message, String userList);

}
