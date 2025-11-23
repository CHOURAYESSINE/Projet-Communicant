package chat;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;

public class chatServer {

    private static ArrayList<ClientHandler> clients = new ArrayList<>();

    private static JFrame frame;
    private static JTextArea logArea;

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> createGUI());

        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            log("Serveur démarré sur le port 6000...");

            while (true) {
                Socket socket = serverSocket.accept();
                log("Nouveau client connecté : " + socket);

                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                client.start();
            }

        } catch (Exception e) {
            log("Erreur serveur : " + e.getMessage());
        }
    }

    public static void log(String txt) {
        System.out.println(txt);
        if (logArea != null) logArea.append(txt + "\n");
    }

    private static void createGUI() {
        frame = new JFrame("Serveur Chat TCP (Images + Privé)");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);

        frame.add(scroll, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    public static void broadcastText(String msg, ClientHandler sender) {
        for (ClientHandler c : clients) {
            if (c != sender) {
                c.sendText("MSG:" + msg);
            }
        }
    }

    private static void updateUsersList() {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler c : clients)
            sb.append(c.username).append(",");

        String list = "USERS:" + sb.toString();

        for (ClientHandler c : clients)
            c.sendText(list);
    }

    static class ClientHandler extends Thread {

        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;

        public String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;

            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (Exception e) {
                log("Erreur init client : " + e.getMessage());
            }
        }

        public void sendText(String msg) {
            try {
                out.writeUTF(msg);
                out.flush();
            } catch (Exception e) {
                log("Erreur envoi texte à " + username);
            }
        }

        public void sendImage(byte[] img, String sender) {
            try {
                out.writeUTF("IMG_FROM:" + sender);
                out.writeInt(img.length);
                out.write(img);
                out.flush();
            } catch (Exception e) {
                log("Erreur envoi image à " + username);
            }
        }

        @Override
        public void run() {
            try {
                username = in.readUTF();
                log(username + " connecté !");
                updateUsersList();

                while (true) {

                    String header = in.readUTF();
                    log("Reçu: " + header);

                    if (header.startsWith("PRIV:")) {
                        String[] p = header.split(":", 3);
                        String dest = p[1];
                        String msg = p[2];

                        for (ClientHandler c : clients) {
                            if (c.username.equals(dest)) {
                                c.sendText("PRIV_FROM:" + username + ":" + msg);
                            }
                        }

                        sendText("PRIV_SENT:" + dest + ":" + msg);
                        continue;
                    }

                    if (header.startsWith("IMG:")) {

                        String[] p = header.split(":", 3);
                        String dest = p[1];
                        String sender = p[2];

                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        log("Image reçue (" + size + " octets)");

                        if (dest.equals("ALL")) {
                            for (ClientHandler c : clients) {
                                if (c != this) c.sendImage(data, sender);
                            }
                        } else {
                            for (ClientHandler c : clients) {
                                if (c.username.equals(dest))
                                    c.sendImage(data, sender);
                            }
                        }
                        continue;
                    }

                    broadcastText(username + " : " + header, this);
                }

            } catch (Exception e) {
                log("Client déconnecté : " + username);
            } finally {
                try { socket.close(); } catch (Exception ex) {}
                clients.remove(this);
                updateUsersList();
            }
        }
    }
}
