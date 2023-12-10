import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {

    private boolean running;

    private Socket socket;

    private DataInputStream inFromServer;
    private volatile DataOutputStream out;

    private Thread fetchThread;

    private long pingTime;

    /*public static void main(String[] args) {

    }*/

    public Client(String ip, int port, ChatClient chatClient) {
        try {
            socket = new Socket(ip, port);

            out = new DataOutputStream(socket.getOutputStream());
            inFromServer = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        fetchThread = new Thread(() -> {
            while (running) {
                String serverFeed = null;
                try {
                    serverFeed = inFromServer.readUTF();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return;
                }

                if (serverFeed.startsWith("#Message#"))
                    chatClient.showMessage(serverFeed
                                            .replaceFirst("#Message#", ""));
                else if (serverFeed.startsWith("#Connections#"))
                    chatClient.updateConnectionList(serverFeed
                                            .replaceFirst("#Connections#", "")
                                            .split(","));
                else if (serverFeed.startsWith("#Pong#"))
                    chatClient.updatePing(System.currentTimeMillis() - pingTime);
                else if (serverFeed.startsWith("#Shutdown#")) {
                    chatClient.shutdown();
                    running = false;
                    return;
                }
            }
        });

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(
                () -> {
                    try {
                        out.writeUTF("#Ping#");
                        out.flush();
                        pingTime = System.currentTimeMillis();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                },
                0, 3, TimeUnit.SECONDS);

        running = true;

        fetchThread.start();
    }

    public void send(String message, String prefix) {

        message = prefix + message;

        try {
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
