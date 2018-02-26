package chat.client.mvp.swing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import chat.base.ChatCommand;
import chat.base.CommandName;
import chat.base.WorkerThread;
import chat.server.ChatServer;

/**
 * The Class ChatClientPresenter. Realize chat client logic.
 */
public class ChatClientPresenter implements Presenter {

  /** The Constant _GREETING_MESSAGE. */
  private final static String MSG_ASK_FOR_USERNAME = "Enter username to start сhat: ";

  private final static String MSG_EMPTY_USRENAME = "Username cannot be empty.";

  private final static String MSG_CANT_CON_SRV = "Can't connect to server " + ChatServer.SERVER_IP
      + ":" + ChatServer.SERVER_PORT + ". Server not started.";

  private final static int TIMEOUT_SESSION_OPEN = 3;

  private ViewSwing viewSwing;

  /** The sever socket. */
  private Socket serverSocket = null;

  // private MessageHandler messageHandler = null;
  private MessageHandler messageHandler = null;

  /** The out stream. */
  private ObjectOutputStream outputStream = null;

  /** The in stream. */
  private ObjectInputStream inputStream = null;

  private AtomicBoolean isConnectionOpen;

  private static final String DEFAULT_WINDOW_NAME = "Java Swing Chat Client";

  public ChatClientPresenter() {
    isConnectionOpen = new AtomicBoolean(false);
  }

  /**
   * Connect to chat server. Open socket, prepare input/output streams, create new thread to data
   * transfer.
   *
   * @param username the user name
   */
  @Override
  public boolean openConnection(String username) {

    isConnectionOpen.set(false);

    boolean res = false;
    try {

      // try to open server connection
      serverSocket = new Socket(ChatServer.SERVER_IP, ChatServer.SERVER_PORT);

      outputStream =
          new ObjectOutputStream(new BufferedOutputStream(serverSocket.getOutputStream()));

    } catch (UnknownHostException uhe) {

      System.out.println(uhe.getMessage());
      return res;

    } catch (IOException ioe) {

      System.out.println(ioe.getMessage());
      return res;

    }

    // send to server enter command
    try {

      new ChatCommand(CommandName.CMDENTER, "", username).send(outputStream);

      inputStream = new ObjectInputStream(new BufferedInputStream(serverSocket.getInputStream()));

      // start message handler
      messageHandler = new MessageHandler();
      messageHandler.start();

    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    int timeout = 1;
    // wait for ok enter command from server
    while (!isConnectionOpen.get() && (timeout <= TIMEOUT_SESSION_OPEN)) {
      try {
        TimeUnit.SECONDS.sleep(1);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      timeout++;
    }

    if (isConnectionOpen.get()) { // we receive ok enter command

      // do that we must do in View on session open
      getViewSwing().onConnectionOpened(username);

      res = true;

    } else {

      // stop message handler
      messageHandler.stop();
      String msg = "Can't connect to the server, timeout " + TIMEOUT_SESSION_OPEN
          + ". Check server, try again or increase open session timeout.";
      System.out.println(msg);
      getViewSwing().showErrorWindow(msg, "Open session timeout.");
      getViewSwing().onConnectionClosed(DEFAULT_WINDOW_NAME);
    }

    return res;

  }


  @Override
  public void closeConnection() {

    // stop message handler thread
    if ((messageHandler != null) && (messageHandler.isRuning())) {
      messageHandler.stop();
    }

    // try to close serversocket

    if (serverSocket != null && serverSocket.isConnected()) {

      // send to server exit command
      new ChatCommand(CommandName.CMDEXIT, "").send(outputStream);

      try {
        serverSocket.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  @Override
  public void sendMsg(String message) {
    new ChatCommand(CommandName.CMDMSG, message).send(outputStream);
    getViewSwing().onSendMsg();
  }

  @Override
  public void sendPrvMsg(String message, String userList) {
    new ChatCommand(CommandName.CMDPRVMSG, message, userList).send(outputStream);
    getViewSwing().onSendMsg();
  }


  class MessageHandler extends WorkerThread {

    @Override
    public void run() {

      // TODO Auto-generated method stub
      ChatCommand chatCommand;
      try {

        while ((chatCommand = (ChatCommand) inputStream.readObject()) != null && isRuning()) {

          System.out.println("ChatClientPresenter.MessageHandler.run()" + chatCommand);

          switch (chatCommand.getCommand()) {
            
            case CMDERR:
              //TODO complete
                break;
            
            case CMDEXIT:
              closeConnection();
              break;
                
            case CMDHLP:
              //TODO complete
              break;
              
            case CMDOK:
              if (chatCommand.getPayload().equals(CommandName.CMDENTER.toString())) {
                isConnectionOpen.set(true);
              }
              break;

            case CMDMSG:
            case CMDPRVMSG:
              getViewSwing().onReceiveMsg(chatCommand.getMessage());
              break;
              
            case CMDUSRLST:
              // Update userList
              getViewSwing().clearChatUserList();
              getViewSwing().updateChatUserList(chatCommand.getPayload().split(" "));;
              break;

            default:
              // TODO save unknown commands to log file
              getViewSwing().showWarningWindow(chatCommand.toString(), "Unknow command");
          }

        }
      } catch (IOException ioe) {
        System.out.println(ioe.getMessage());
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

  }

  /**
   * Print greeting message to enter field.
   */
  public void onClientStart() {
    getViewSwing().onConnectionOpening(DEFAULT_WINDOW_NAME);
    getViewSwing().showMsgOnChatPane(MSG_ASK_FOR_USERNAME);
  }

  public void stop() {
    // TODO Auto-generated method stub
    System.out.println("Closing client...");

    System.out.println("Send exit command");
    new ChatCommand(CommandName.CMDEXIT, "").send(outputStream);

    System.out.println("Stopping message handler thread, closing ServerSocket");
    closeConnection();

    System.out.println("Client stopped.");
  }

  @Override
  public void setView(ViewSwing view) {
    this.viewSwing = view;

  }

  public ViewSwing getViewSwing() {
    if (viewSwing == null) {
      throw new IllegalStateException("The viewSwing is not set.");
    } else {
      return this.viewSwing;
    }
  }

}
