package chat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javax.swing.text.*;

public class chatClient extends JFrame {

    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton;
    private JButton imageButton;

    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String username;

    public chatClient() {

        username = JOptionPane.showInputDialog(this, "Entrez votre nom :");
        if (username == null || username.trim().isEmpty()) {
            username = "Client";
        }

        setTitle("Chat Client - " + username);
        setSize(700, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        messageField = new JTextField();
        sendButton = new JButton("Envoyer");
        imageButton = new JButton("📷 Image");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(1,2));
        buttonPanel.add(sendButton);
        buttonPanel.add(imageButton);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));

        add(chatScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        add(userScroll, BorderLayout.EAST);

        connectToServer("127.0.0.1", 6000);

        sendButton.addActionListener(e -> sendMessage());
        imageButton.addActionListener(e -> sendImage());
        messageField.addActionListener(e -> sendMessage());

        new Thread(new NetworkReceiver()).start();

        setVisible(true);
    }

    private void appendText(String txt) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), txt + "\n", null);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    private void appendImageInChat(BufferedImage img, String sender) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();

            doc.insertString(doc.getLength(), sender + " :\n", null);

            Image newImg = img.getScaledInstance(150, -1, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(newImg);

            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(icon);

            doc.insertString(doc.getLength(), "\n\n", null);

        } catch (Exception e) {}
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);

            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            out.writeUTF(username);
            appendText("Connecté au serveur.");

        } catch (Exception e) {
            appendText("Erreur de connexion.");
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();

        if (!msg.isEmpty()) {

            String selectedUser = userList.getSelectedValue();

            try {
                if (selectedUser != null) {
                    out.writeUTF("PRIV:" + selectedUser + ":" + msg);
                    appendText("Moi → (privé à " + selectedUser + ") : " + msg);
                } else {
                    out.writeUTF(msg);
                    appendText("Moi : " + msg);
                }
            } catch (Exception e) {}

            messageField.setText("");
        }
    }

    private void sendImage() {

        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            BufferedImage img = ImageIO.read(chooser.getSelectedFile());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);

            byte[] imgBytes = baos.toByteArray();

            String target = userList.getSelectedValue();
            if (target == null) target = "ALL";

            out.writeUTF("IMG:" + target + ":" + username);

            out.writeInt(imgBytes.length);
            out.write(imgBytes);
            out.flush();

            appendText("📤 Image envoyée.");

        } catch (Exception e) {
            appendText("❌ Erreur envoi image.");
        }
    }

    private class NetworkReceiver implements Runnable {

        public void run() {
            try {
                while (true) {

                    String header = in.readUTF();

                    if (header.startsWith("USERS:")) {
                        updateUserList(header.substring(6));
                        continue;
                    }

                    if (header.startsWith("IMG_FROM:")) {

                        String sender = header.substring(9);

                        int length = in.readInt();
                        byte[] imgBytes = new byte[length];
                        in.readFully(imgBytes);

                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imgBytes));

                        appendText("[Image reçue de " + sender + "]");
                        appendImageInChat(img, sender);

                        continue;
                    }

                    if (header.startsWith("PRIV_FROM:")) {
                        String[] p = header.split(":", 3);
                        String sender = p[1];
                        String msg = p[2];

                        appendText("🔒 Privé de " + sender + " : " + msg);
                        continue;
                    }

                    if (header.startsWith("PRIV_SENT:")) {
                        String[] p = header.split(":", 3);
                        appendText("✔ Privé envoyé à " + p[1]);
                        continue;
                    }

                    appendText(header);
                }

            } catch (Exception e) {
                appendText("\nDéconnecté du serveur.");
            }
        }
    }

    private void updateUserList(String list) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : list.split(",")) {
                if (!u.isEmpty() && !u.equals(username))
                    userListModel.addElement(u);
            }
        });
    }

    public static void main(String[] args) {
        new chatClient();
    }
}
