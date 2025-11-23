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
        frame = new JFrame("Serveur Chat TCP (Texte + Image + Fichier + Privé)");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);

        frame.add(scroll, BorderLayout.CENTER);
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
            this.socket = socket;
            try {
                in = new DataInputStream(socket.getInputStream());
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
                            if (client.username.equals(dest)) client.sendText("PRIV_FROM:" + username + ":" + msg);
                        sendText("PRIV_SENT:" + dest + ":" + msg);
                        continue;
                    }

                    // Image
                    if (header.startsWith("IMG:")) {
                        String[] p = header.split(":",3);
                        String dest = p[1], sender = p[2];
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        log("Image reçue (" + size + " octets)");

                        if (dest.equals("ALL")) {
                            for (ClientHandler client : clients)
                                if (client != this) client.sendImage(data, sender);
                        } else {
                            for (ClientHandler clientDest : clients)
                                if (clientDest.username.equals(dest))
                                    clientDest.sendImage(data, sender);
                        }
                        continue;
                    }

                    // Fichier
                    if (header.startsWith("FILE:")) {
                        String[] p = header.split(":",3);
                        String dest = p[1], fileName = p[2];
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);

                        log("Fichier reçu : " + fileName + " (" + size + " octets)");

                        if (dest.equals("ALL")) {
                            for (ClientHandler client : clients)
                                if (client != this) client.sendFile(data, fileName, username);
                        } else {
                            for (ClientHandler clientDest : clients)
                                if (clientDest.username.equals(dest))
                                    clientDest.sendFile(data, fileName, username);
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
