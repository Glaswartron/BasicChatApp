import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Server {

    ServerSocket socket;

    private static volatile boolean running = false;

    private static final int THREADS = 8;

    private static Executor exec;

    private Thread connectionListenerThread;

    private List<String> messages;
    private List<String> connections;

    public static void main(String[] args) {
        Server server = new Server(5000);
    }

    public Server(int port) {
        try {
            exec = new ThreadPoolExecutor(
                    THREADS,
                    THREADS,
                    1L,
                    TimeUnit.MINUTES,
                    new SynchronousQueue<>(),
                    (runnable, executor) -> {
                        // Rejected execution handler
                        ((ChatRunnable)runnable).rejectClient();
                    });

            messages = Collections.synchronizedList(new ArrayList<>());
            connections = Collections.synchronizedList(new ArrayList<>());

            socket = new ServerSocket(port);

            running = true;

            connectionListenerThread = new Thread(() -> {
                System.out.println("Waiting for clients on port " +
                        socket.getLocalPort() + "...");

                while (running) {
                    Socket connection = null;
                    try {
                        connection = socket.accept();
                    } catch (SocketException se) {
                        System.out.println("No longer waiting for clients on port " + socket.getLocalPort());
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }

                    exec.execute(new ChatRunnable(connection));
                }
            });

            connectionListenerThread.start();

            Scanner in = new Scanner(System.in);
            while (running) {
                if (in.nextLine().equals("ShutdownServer"))
                    running = false;
            }

            System.out.println("Socket is about to close");
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ChatRunnable implements Runnable {

        public Socket connection = null;
        public boolean connectionRunning; // TODO: volatile

        private DataInputStream in;
        private volatile DataOutputStream out;

        private ArrayList<String> localMessages;

        private ScheduledExecutorService scheduledExecutor;

        private String name;

        public ChatRunnable(Socket connection) {
            this.connection = connection;

            localMessages = new ArrayList<>();

            name = connection.getRemoteSocketAddress().toString();
            connections.add(name);

            try {
                in = new DataInputStream(connection.getInputStream());
                out = new DataOutputStream(connection.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            connectionRunning = true;

            messages.add("Connected: " + connection.getRemoteSocketAddress());

            scheduledExecutor = Executors.newScheduledThreadPool(1);
            scheduledExecutor.scheduleAtFixedRate(
                    () -> {
                        if (connectionRunning) {
                            if (messages.size() > localMessages.size()) {
                                try {
                                    for (int i = localMessages.size(); i < messages.size(); i++) {
                                        String msg = messages.get(i);
                                        out.writeUTF("#Message#" + msg);
                                        localMessages.add(msg);
                                    }
                                    out.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    },
                    0, 500, TimeUnit.MILLISECONDS);

            scheduledExecutor.scheduleAtFixedRate(() -> {
                if (connectionRunning) {
                    String connectionsList = "#Connections#" + connections.stream().reduce("", (x, y) -> x + "," + y);
                    writeToClient(connectionsList);
                }
            },
                    0, 2, TimeUnit.SECONDS);

            String message = "";
            while (true) {
                if (!running) {
                    writeToClient("#Shutdown#");
                    scheduledExecutor.shutdown();
                    return;
                }

                try {
                    message = in.readUTF();
                } catch (EOFException | SocketException e) {
                    messages.add("Disconnected: " + name);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                System.out.println(message);

                if (message.startsWith("#Message#")) {
                    message = message.replaceFirst("#Message#", "");
                    messages.add(name + ": " + message);
                } else if (message.startsWith("#Name#")) {
                    String oldName = name;
                    name = message.replaceFirst("#Name#", "");
                    connections.set(connections.indexOf(oldName), name);
                    messages.add(oldName + " is now called " + name);
                } else if (message.startsWith("#Ping#")) {
                    writeToClient("#Pong#");
                } else if (message.startsWith("#Disconnect#")) {
                    connectionRunning = false;
                    messages.add(name + " disconnected");
                    connections.remove(name);
                    scheduledExecutor.shutdown();
                    return;
                }
            }
        }

        public void rejectClient() {
            try {
                System.out.println("Rejecting client " + connection.getRemoteSocketAddress());
                out.writeUTF("Sorry, the server is very busy right now. Try again later. Disconnected.");
                out.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void writeToClient(String message) {
            try {
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
