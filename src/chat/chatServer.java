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
        frame = new JFrame("Serveur Chat TCP");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);

        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);
        frame.setVisible(true);
    }

    public static void broadcastText(String msg, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) client.sendText("MSG:" + msg);
        }
    }

    private static void updateUsersList() {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler client : clients) sb.append(client.username).append(",");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        String list = "USERS:" + sb.toString();
        for (ClientHandler client : clients) client.sendText(list);
    }

    static class ClientHandler extends Thread {

        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        public String username;

        public ClientHandler(Socket socket) {
            try {
                this.socket = socket;
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (Exception e) {
                log("Erreur init client : " + e.getMessage());
            }
        }

        public void sendText(String msg) {
            try { out.writeUTF(msg); out.flush(); }
            catch (Exception e) { log("Erreur envoi texte à " + username); }
        }

        public void sendImage(byte[] img, String sender) {
            try {
                out.writeUTF("IMG_FROM:" + sender);
                out.writeInt(img.length);
                out.write(img);
                out.flush();
            } catch (Exception e) { log("Erreur envoi image à " + username); }
        }

        public void sendFile(byte[] fileData, String fileName, String sender) {
            try {
                out.writeUTF("FILE_FROM:" + sender + ":" + fileName);
                out.writeInt(fileData.length);
                out.write(fileData);
                out.flush();
            } catch (Exception e) { log("Erreur envoi fichier à " + username); }
        }

        public void sendAudio(byte[] audioData, String audioName, String sender) {
            try {
                out.writeUTF("AUDIO_FROM:" + sender + ":" + audioName);
                out.writeInt(audioData.length);
                out.write(audioData);
                out.flush();
            } catch (Exception e) { log("Erreur envoi audio à " + username); }
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

                    // Privé
                    if (header.startsWith("PRIV:")) {
                        String[] p = header.split(":",3);
                        String dest = p[1], msg = p[2];
                        for (ClientHandler client : clients)
                            if (client.username.equals(dest))
                                client.sendText("PRIV_FROM:" + username + ":" + msg);
                        sendText("PRIV_SENT:" + dest + ":" + msg);
                        continue;
                    }

                    // Image
                    if (header.startsWith("IMG:")) {
                        String[] p = header.split(":",3);
                        String dest   = p[1];
                        String sender = p[2];
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        if (dest.equals("ALL")) {
                            for (ClientHandler c : clients)
                                if (c != this) c.sendImage(data, sender);
                        } else {
                            for (ClientHandler c : clients)
                                if (c.username.equals(dest))
                                    c.sendImage(data, sender);
                        }
                        continue;
                    }

                    // Fichier
                    if (header.startsWith("FILE:")) {
                        String[] p = header.split(":",3);
                        String dest     = p[1];
                        String fileName = p[2];
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        if (dest.equals("ALL")) {
                            for (ClientHandler c : clients)
                                if (c != this) c.sendFile(data, fileName, username);
                        } else {
                            for (ClientHandler c : clients)
                                if (c.username.equals(dest))
                                    c.sendFile(data, fileName, username);
                        }
                        continue;
                    }

                    // Audio
                    if (header.startsWith("AUDIO:")) {
                        String[] p = header.split(":",3);
                        String dest = p[1];
                        String audioName = p[2];
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        if (dest.equals("ALL")) {
                            for (ClientHandler c : clients)
                                if (c != this) c.sendAudio(data, audioName, username);
                        } else {
                            for (ClientHandler c : clients)
                                if (c.username.equals(dest))
                                    c.sendAudio(data, audioName, username);
                        }
                        continue;
                    }

                    // Message public
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
