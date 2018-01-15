package chat.client.mvp.swing;

import javax.swing.SwingUtilities;

// TODO test swing app with JUnit http://www.javacreed.com/testing-swing-application/
// TODO use MVC pattern http://www.javacreed.com/testing-swing-application/

/**
 * The Class ChatClient. Main class to start chat client.
 */
public class ChatClient {

  /**
   * The main method. Run GUI in Event Dispatch Thread. Initialize View and Presenter.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          
          // Initialize View and Presenter
          final ChatClientPresenter presenter = new ChatClientPresenter();
          final ChatClientView mainWindow = new ChatClientView();
          presenter.setView(mainWindow);
          mainWindow.setPresenter(presenter);
          
          // Show main window
          mainWindow.setVisible(true);         

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }
}
