package chat.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static chat.base.CommandName.CMDDLM;
import static chat.base.CommandName.CMDENTER;
import static chat.base.CommandName.CMDERR;
import static chat.base.CommandName.CMDMSG;
import static chat.base.CommandName.CMDOK;
import static chat.base.CommandName.CMDULDLM;
import static chat.base.CommandName.CMDUSRLST;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import chat.base.ClientPresenter;
import chat.base.Command;
import chat.base.User;
import chat.base.CommandName;
import chat.base.WorkerThread;

/**
 * It implements server side part of chat application for one chat client.
 * Handle input/output streams of client connection. Maintain handler storage
 * and user lists.
 * 
 * @see Server
 */
public class ClientHandler extends WorkerThread {
	private static final String MSG_OPEN_CONNECTION = "Open connection for user ";
	/**
	 * Logger for this class
	 */
	private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
	private static final Logger loggerDebugMDC = LoggerFactory.getLogger("debug.MDC");

	private static final String MSG_ACCPT_CLIENT = "Accepted client connection from ";

	public static final String ERR_USRS_NOT_FOUND = "User(s) not found. Username list: ";

	/** The Constant ERR_NAME_EXISTS_MSG. */
	public static final String ERR_NAME_EXISTS_MSG = "Username already exists or wrong.";

	/** The Constant MSG_WLC_USR. */
	public static final String MSG_WLC_USR = "login";

	/** The Constant MSG_EXIT_USR. */
	public static final String MSG_EXIT_USR = "logout";
	private static final String MSG_CLOSE_CONNECTION = "Close connection for ";

	/** The client socket. */
	private Socket clientSocket = null;

	/** The input stream. */
	private ObjectInputStream inputStream = null;

	/** The output stream. */
	private ObjectOutputStream outputStream = null;

	/** The client session handler storage. */
	private ConcurrentHashMap<String, ClientHandler> clientHandlers;

	/** The chat user. */
	private User user = null;

	/** The is session opened flag. */
	private AtomicBoolean isSessionOpened;

	/**
	 * Instantiates a new chat handler.
	 *
	 * @param clientSocket
	 *            the client socket
	 * @param clientHandlers
	 *            the handler storage
	 */
	public ClientHandler(Socket clientSocket, ConcurrentHashMap<String, ClientHandler> clientHandlers) {
		this.clientSocket = clientSocket;
		this.clientHandlers = clientHandlers;
		this.isSessionOpened = new AtomicBoolean(false);
	}

	/**
	 * Override {@link java.lang.Thread#run run()} method. Run while open current
	 * socket input stream. Process command received from chat client.
	 * 
	 * @see java.lang.Thread#run()
	 * @see Server
	 * 
	 */
	@Override
	public void run() {

		try {

			inputStream = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
			outputStream = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));

			String ip = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString()
					.replace("/", "");
			logger.info("run() - {}", MSG_ACCPT_CLIENT + ip); //$NON-NLS-1$
			System.out.println(MSG_ACCPT_CLIENT + ip);

			// Command command;

			// Reading commands from the current client input socket while the handler is
			// running
			// while ((command = (Command) inputStream.readObject()) != null && isRunning())
			// {
			while (isRunning()) {

				Command command = (Command) inputStream.readObject();
				processCommand(command);

			}

		} catch (ClassNotFoundException | IOException e) {
			logger.error("run()", e); //$NON-NLS-1$

		} finally {

			// First of all we remove this handler from clientHandlers storage to prevent
			// receiving messages
			if (user != null) {
				clientHandlers.remove(user.getUsername());
			}

			// print console message about closing connection
			String msg = (user != null) ? user.getUsername() : "";
			msg = MSG_CLOSE_CONNECTION + msg;
			logger.info("run() - {}", msg); //$NON-NLS-1$
			System.out.println(msg);

			// Send a message to all clients about the current user's exit
			sendToAllChatClients(
					new Command(CMDMSG, getCurrentDateTime() + " " + user.getUsername() + " " + MSG_EXIT_USR));

			// Send update user list command
			sendToAllChatClients(new Command(CMDUSRLST, "", getUserNamesListInString()));

			MDC.clear();
			user = null;
			closeClientSocket();

			// stop();

		}
	}

	@Override
	public void stop() {
		super.stop();
		// First close the input stream to release the while circle in run() method
		closeInputStream();
	}

	private synchronized void closeInputStream() {
		if (clientSocket != null && inputStream != null) {
			try {
				inputStream.close();
				inputStream = null;
			} catch (IOException e) {
				logger.error("closeInputStream()", e); //$NON-NLS-1$
			}
		}
	}

	private synchronized void closeClientSocket() {

		if (clientSocket != null) {
			try {
				clientSocket.close();
				clientSocket = null;
			} catch (IOException e) {
				logger.error("closeClientSocket()", e); //$NON-NLS-1$
			}
		}
	}

	private void processCommand(Command command) {
		// loggerRoot.debug("processCommand(Command) - user {}, command {}", ((user ==
		// null) ? "" :
		// user.getUsername()), //$NON-NLS-1$
		// command);

		// System.out.println(((user == null) ? "" : user.getUsername()) + command);

		loggerDebugMDC.debug(command.toString());

		// ignore all command except CMDENTER while session not opened
		if (!isSessionOpened.get() && command.getCommandName() != CMDENTER) {
			// loggerRoot.debug("processCommand(Command) - end"); //$NON-NLS-1$
			return;
		}

		// chat command processing
		switch (command.getCommandName()) {

		case CMDERR:
			// TODO test it with unit test
			System.err.println(user.getUsername() + ": error: " + command.getMessage());
			// getView().show WarningWindow(command.toString(), WRN_UNKNOWN_COMMAND_MSG);
			logger.error("ProcessCommandThread.run() {}", command.getMessage());
			break;

		case CMDEXIT:
			stop(); // stop current ClientHandler thread (set isRuning() to false)
			break;

		case CMDENTER:

			// get username
			String userName = command.getPayload();

			// TODO check for username uniquely

			if (!userName.isEmpty()) { // check for empty username

				// add current handler to handler storage and now we can communicate with other
				// chat
				// clients
				// using user name as a key
				MDC.put("username", userName);
				clientHandlers.put(userName, this);

				isSessionOpened.set(true); // set flag that current session is opened

				// create new user
				user = new User(userName);

				// send ok enter command to confirm session opening
				new Command(CMDOK, "", CMDENTER.toString()).send(outputStream);

				// TODO what if isSessionOpened set to true but we cant send ok enter command to
				// client check with unit tests
				// send to all users usrlst command
				sendToAllChatClients(new Command(CMDUSRLST, "", getUserNamesListInString()));

				// send to all welcome message
				sendToAllChatClients(
						new Command(CMDMSG, getCurrentDateTime() + " " + user.getUsername() + " " + MSG_WLC_USR));

				// print to server console
				String msg = MSG_OPEN_CONNECTION + userName;
				logger.info("run() {}", msg);
				System.out.println(msg);

			} else {

				// if username is empty send error to client, print to console and save to log
				String msg = ERR_NAME_EXISTS_MSG + " " + userName;
				new Command(CMDERR, msg).send(outputStream);
				logger.warn("ClientHandler.processCommand() {}", msg);
				System.out.println(msg);
			}
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
			String message = getCurrentDateTime() + " " + user.getUsername() + ": " + command.getMessage();

			// IF private message recipient list is empty, send message to all clients
			if (usrSet.size() == 0) {
				sendToAllChatClients(new Command(CMDMSG, message));

				// Send only for recipient user list
			} else {

				// Add sender to recepient list
				usrSet.add(user.getUsername());

				// System.out.println("ClientHandler.run()" + usrSet.toString());

				// Create storage for not founded user names
				ArrayList<String> notFoundUserList = new ArrayList<String>();

				// Send message to users in list
				for (String key : usrSet) {

					// Search chatHandler by chat user name string
					ClientHandler clientHandler = clientHandlers.get(key);

					// If found send message
					if (clientHandler != null) {
						new Command(CMDMSG, message).send(clientHandler.outputStream);
						;

						// If not found, add to list
					} else {
						notFoundUserList.add(key);
					}
				}

				// If not found user list not empty, send error message back to client
				if (!notFoundUserList.isEmpty()) {
					String errMessage = notFoundUserList.toString().replaceAll("\\[|\\]", "").replaceAll(", ",
							CMDULDLM.toString());
					System.out.println("ClientHandler.run()" + notFoundUserList.toString());
					new Command(CMDERR, ERR_USRS_NOT_FOUND + errMessage).send(outputStream);
				}

			}

			/*
			 * // send private message for (ClientHandler chatHandler : clientHandlers) { if
			 * ((usrSet.size() == 0) // send message to all user or only to users in private
			 * // message user list || (usrSet.size() > 0 &&
			 * usrSet.contains(chatHandler.chatUser.getUsername()))) {
			 * 
			 * String message = getCurrentDateTime() + " " + user.getUsername() + ": " +
			 * chatCommand.getMessage(); new Command(chatCommand.getCommandName(), message)
			 * .send(chatHandler.outputStream); } else { // username not found print message
			 * to server console System.out.println("Command \"" + chatCommand.toString() +
			 * "\". Username " + chatHandler.chatUser.getUsername() + " not found"); } }
			 */
			break;

		default:
			String errMessage = ClientPresenter.WRN_UNKNOWN_COMMAND_MSG + " " + command;
			new Command(CMDERR, errMessage).send(outputStream);
			logger.warn(errMessage);
			System.out.println(errMessage);
		}

	}

	/**
	 * Gets the current date time.
	 *
	 * @return the current date time string
	 */
	private String getCurrentDateTime() {

		String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
		return currentTime;

		// return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd
		// HH:mm:ss"));
		// return LocalDateTime.now().toString();
		// return "1";
	}

	/**
	 * Send chat command to all chat clients.
	 *
	 * @param command
	 *            the command to send
	 */
	private void sendToAllChatClients(Command command) {
		for (ClientHandler clientHandler : clientHandlers.values()) {
			command.send(clientHandler.outputStream);
		}
	}

	/**
	 * Return all chat user names in one string separated by
	 * {@link CommandName#CMDDLM}. Used in {@link CommandName#CMD_USRLST usrlst}
	 * command.
	 *
	 * @return the list of user names in string
	 */
	private String getUserNamesListInString() {

		return clientHandlers.keySet().toString().replaceAll("\\[|\\]", "").replaceAll(", ", CMDDLM.toString());
	}

	// Send to all users except current updated usrlst command
	/*
	 * HashSet<ClientHandler> excludeChatHandler = new HashSet<>();
	 * excludeChatHandler.add(this); sendToAllChatClients(new Command(CMDUSRLST, "",
	 * getUserNamesInString()), excludeChatHandler);
	 */

	// Send to all exit message

	/*
	 * sendToAllChatClients(new Command(CMDMSG, currentTime + " " +
	 * user.getUsername() + " " + MSG_EXIT_USR), excludeChatHandler);
	 */

	/**
	 * Send to all chat clients.
	 *
	 * @param command
	 *            the command
	 * @param excludeChatHandlerList
	 *            the exclude chat handler list
	 */
	/*
	 * private void sendToAllChatClients(Command command, Set<ClientHandler>
	 * excludeChatHandlerList) { for (ClientHandler chatHandler : clientHandlers) {
	 * if (!excludeChatHandlerList.contains(chatHandler)) {
	 * command.send(chatHandler.outputStream); } } }
	 */

}
