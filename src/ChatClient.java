import javax.swing.*;

public class ChatClient {
    private final Client client;

    private static JFrame frame;

    private JTextPane chatTextPane;
    private JTextField messageTextField;
    private JPanel panel;
    private JButton sendButton;
    private JTextField nameTextField;
    private JPanel sideboard;
    private JLabel pingLabel;
    private JLabel nameLabel;
    private JButton setNameButton;
    private JTextPane connectedTextPane;

    public static void main(String[] args) {
        frame = new JFrame("ChatClient");
        frame.setContentPane(new ChatClient().panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }

    public ChatClient() {
        String ip = (String)JOptionPane.showInputDialog(
                frame,
                "Enter server IP address",
                "IP",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "20.23.238.140");

        if (ip == null || ip.isEmpty())
            System.exit(1);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                client.send("", "#Disconnect#");

                System.exit(0);
            }
        });

        client = new Client(ip, 5000, this); // !

        sendButton.addActionListener(e -> sendMessage());
        setNameButton.addActionListener(e -> setName());
    }

    public void sendMessage() {
        String message = messageTextField.getText();

        if (!message.isEmpty()) {
            client.send(message, "#Message#");
        }

        messageTextField.setText("");
    }

    public void setName() {
        String name = nameTextField.getText();
        if (!name.isEmpty()) {
            client.send(name, "#Name#");
            nameLabel.setText("Name: " + name);
        }
    }

    public void showMessage(String message) {
        chatTextPane.setText(chatTextPane.getText() + "\n" + message);
    }

    public void updateConnectionList(String[] connections) {
        StringBuilder sb = new StringBuilder();
        for (String connection : connections) {
            sb.append(connection).append("\n");
        }
        String connectionList = sb.toString();
        connectedTextPane.setText("Connected: \n" + connectionList);
    }

    public void updatePing(long ping) {
        pingLabel.setText("Ping: " + ping);
    }

    public void shutdown() {
        chatTextPane.setText(chatTextPane.getText() + "\nServer is shutting down!");
    }
}
