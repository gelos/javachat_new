package chat.server;

import static chat.base.CommandName.CMDDLM;
import static chat.base.CommandName.CMDENTER;
import static chat.base.CommandName.CMDERR;
import static chat.base.CommandName.CMDMSG;
import static chat.base.CommandName.CMDOK;
import static chat.base.CommandName.CMDULDLM;
import static chat.base.CommandName.CMDUSRLST;
import static chat.base.Constants.ERR_NAME_EXISTS_MSG;
import static chat.base.Constants.ERR_USRS_NOT_FOUND;
import static chat.base.Constants.MSG_CLOSE_CONNECTION;
import static chat.base.Constants.MSG_EXIT_USR;
import static chat.base.Constants.MSG_OPEN_CONNECTION;
import static chat.base.Constants.MSG_WLC_USR;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import chat.base.ChatSession;
import chat.base.Command;
import chat.base.CommandName;
import chat.base.Constants;
import chat.base.User;

public class ServerChatSession extends ChatSession {

  /** The client session handlers thread-safe storage. */
  private ConcurrentHashMap<String, ChatSession> chatSessionStorage;

  public ServerChatSession(Socket clientSocket,
      ConcurrentHashMap<String, ChatSession> chatSessionStorage) {
    //super(clientSocket);
    super(Constants.THREAD_NAME_SRV);
    runCommandHandler(clientSocket);
    this.chatSessionStorage = chatSessionStorage;
  }

  @Override
  public void processCommand(Command command) {

    super.processCommand(command);

    // ignore all command except CMDENTER while session not opened
    if (!getIsSessionOpenedFlag() && command.getCommandName() != CMDENTER) {
      // loggerRoot.debug("processCommand(Command) - end"); //$NON-NLS-1$
      return;
    }

    // chat command processing
    switch (command.getCommandName()) {

      case CMDERR:
        // TODO test it with unit test
        System.err.println(user.getUsername() + ": error: " + command.getMessage());
        // getView().show WarningWindow(command.toString(), WRN_UNKNOWN_COMMAND_MSG);
        // logger.error("ProcessCommandThread.run() {}", Command.getMessage());
        break;

      case CMDEXIT:
        // TODO stop & join CommandHandler in closeSession

        boolean sendEXTCMD = false;
        closeSession(sendEXTCMD);

        // stop(); // stop current ServerCommandHandler thread (set isRuning() to false)
        break;

      case CMDENTER:

        // get username
        String userName = command.getPayload();

        openSession(userName);
        break;

      case CMDHLP:
        // TODO complete
        break;

      case CMDMSG:
      case CMDPRVMSG:

        // Get user list from payload
        String[] usrList = new String[0];
        if (!command.getPayload().isEmpty()) {
          usrList = command.getPayload().split(CMDULDLM.toString());
        }

        Set<String> usrSet = new HashSet<String>(Arrays.asList(usrList));

        // System.out.println(usrSet.toString());

        // Prepare message
        String message =
            getCurrentDateTime() + " " + user.getUsername() + ": " + command.getMessage();

        // IF private message recipient list is empty, send message to all clients
        if (usrSet.size() == 0) {
          sendToAllChatClients(new Command(CMDMSG, message));

          // Send only for recipient user list
        } else {

          // Add sender to recipient list
          usrSet.add(user.getUsername());

          // System.out.println("ServerCommandHandler.run()" + usrSet.toString());

          // Create storage for not founded user names
          ArrayList<String> notFoundUserList = new ArrayList<String>();

          // Send message to users in list
          for (String key : usrSet) {

            // Search chatHandler by chat user name string
            // ServerChatSession serverCommandHandler = chatSessionStorage.get(key);
            // ServerCommandHandler serverCommandHandler = chatSessionStorage.get(key);
            ChatSession serverCommandHandler = chatSessionStorage.get(key);

            // If found send message
            if (serverCommandHandler != null) {
              // new Command(CMDMSG, message)
              // .send(serverCommandHandler.getCommandHandler().outputStream);;
              serverCommandHandler.sendCommand(new Command(CMDMSG, message));

              // If not found, add to list
            } else {
              notFoundUserList.add(key);
            }
          }

          // If not found user list not empty, send error message back to client
          if (!notFoundUserList.isEmpty()) {
            String errMessage = notFoundUserList.toString().replaceAll("\\[|\\]", "")
                .replaceAll(", ", CMDULDLM.toString());
            System.out.println("ServerCommandHandler.run()" + notFoundUserList.toString());
            // new Command(CMDERR, ERR_USRS_NOT_FOUND +
            // errMessage).send(commandHandler.outputStream);
            sendCommand(new Command(CMDERR, ERR_USRS_NOT_FOUND + errMessage));
          }

        }

        /*
         * // send private message for (ServerCommandHandler chatHandler : chatSessionStorage) { if
         * ((usrSet.size() == 0) // send message to all user or only to users in private // message
         * user list || (usrSet.size() > 0 && usrSet.contains(chatHandler.chatUser.getUsername())))
         * {
         * 
         * String message = getCurrentDateTime() + " " + user.getUsername() + ": " +
         * chatCommand.getMessage(); new Command(chatCommand.getCommandName(), message)
         * .send(chatHandler.outputStream); } else { // username not found print message to server
         * console System.out.println("Command \"" + chatCommand.toString() + "\". Username " +
         * chatHandler.chatUser.getUsername() + " not found"); } }
         */
        break;

      default:
        String errMessage = Constants.WRN_UNKNOWN_COMMAND_MSG + " " + command;
        // new Command(CMDERR, errMessage).send(commandHandler.outputStream);
        sendCommand(new Command(CMDERR, errMessage));
        // logger.warn(errMessage);
        System.out.println(errMessage);
    }


  }

  /**
   * Gets the current date time.
   *
   * @return the current date time string
   */
  private String getCurrentDateTime() {

    String currentTime =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    return currentTime;

    // return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd
    // HH:mm:ss"));
    // return LocalDateTime.now().toString();
    // return "1";
  }

  /**
   * Send chat command to all chat clients.
   *
   * @param command the command to send
   */
  private void sendToAllChatClients(Command command) {
    // for (ServerChatSession serverCommandHandler : chatSessionStorage.values()) {
    // for (ServerCommandHandler serverCommandHandler : chatSessionStorage.values()) {
    for (ChatSession serverCommandHandler : chatSessionStorage.values()) {
      // Command.send(serverCommandHandler.getCommandHandler().outputStream);
      serverCommandHandler.sendCommand(command);
    }
  }

  /**
   * Return all chat user names in one string separated by {@link CommandName#CMDDLM}. Used in
   * {@link CommandName#CMD_USRLST usrlst} command.
   *
   * @return the list of user names in string
   */
  private String getUserNamesListInString() {

    return chatSessionStorage.keySet().toString().replaceAll("\\[|\\]", "").replaceAll(", ",
        CMDDLM.toString());
  }

  @Override
  public void openSession(String userName) {
    // TODO check for username uniquely

    super.openSession(userName);

    if (!userName.isEmpty()) { // check for empty username

      // add current handler to handler storage and now we can communicate with other
      // chat
      // clients
      // using user name as a key
      MDC.put("username", userName);
      chatSessionStorage.put(userName, this);

      isSessionOpenedFlag.set(true); // set flag that current session is opened
      // isChatSessionOpenedFlag.set(true);

      // create new user
      this.user = new User(userName);

      // send ok enter command to confirm session opening
      sendCommand(new Command(CMDOK, "", CMDENTER.toString()));

      // TODO what if isSessionOpenedFlag set to true but we cant send ok enter command to
      // client check with unit tests
      // send to all users usrlst command
      sendToAllChatClients(new Command(CMDUSRLST, "", getUserNamesListInString()));

      System.out.println("ServerChatSession.openSession() CMDUSRLST " + getUserNamesListInString());

      // send to all welcome message
      sendToAllChatClients(
          new Command(CMDMSG, getCurrentDateTime() + " " + user.getUsername() + " " + MSG_WLC_USR));

      // print to server console
      String msg = MSG_OPEN_CONNECTION + userName;
      // logger.info("run() {}", msg);
      System.out.println(msg);

    } else {

      // if username is empty send error to client, print to console and save to log
      String msg = ERR_NAME_EXISTS_MSG + " " + userName;
      sendCommand(new Command(CMDERR, msg));
      // TODO add logger to the class
      // logger.warn("ServerCommandHandler.processCommand() {}", msg);
      System.out.println(msg);
    }


  }


  @Override
  public void closeSession(boolean sendEXTCMD) {

    // First of all we remove this handler from chatSessionStorage storage to
    // prevent receiving messages
    if (user != null) {
      chatSessionStorage.remove(user.getUsername());
    }

    // print console message about closing connection
    String msg = (user != null) ? user.getUsername() : "";
    msg = MSG_CLOSE_CONNECTION + msg;
    // CommandHandler.logger.info("run() - {}", msg); //$NON-NLS-1$
    System.out.println(msg);

    // Send a message to all clients about the current user's exit
    sendToAllChatClients(
        new Command(CMDMSG, getCurrentDateTime() + " " + user.getUsername() + " " + MSG_EXIT_USR));

    // Send update user list command
    sendToAllChatClients(new Command(CMDUSRLST, "", getUserNamesListInString()));

    super.closeSession(sendEXTCMD);

  }

}