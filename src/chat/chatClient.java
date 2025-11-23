package chat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.awt.Desktop;
import javax.imageio.ImageIO;

public class chatClient extends JFrame {

    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton, imageButton, fileButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    public chatClient() {
        username = JOptionPane.showInputDialog(this, "Entrez votre nom :");
        if (username == null || username.trim().isEmpty()) username = "Client";

        setTitle("Chat Client - " + username);
        setSize(700,520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        messageField = new JTextField();
        sendButton = new JButton("Envoyer");
        imageButton = new JButton("📷 Image");
        fileButton  = new JButton("📁 Fichier");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(1,3));
        buttonPanel.add(sendButton); buttonPanel.add(imageButton); buttonPanel.add(fileButton);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150,0));

        add(chatScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        add(userScroll, BorderLayout.EAST);

        connectToServer("127.0.0.1",6000);

        sendButton.addActionListener(e -> sendMessage());
        imageButton.addActionListener(e -> sendImage());
        fileButton.addActionListener(e -> sendFile());
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
            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(new ImageIcon(newImg));
            doc.insertString(doc.getLength(), "\n\n", null);
        } catch (Exception e) {}
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host,port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(username);
            appendText("Connecté au serveur.");
        } catch (Exception e) {
            appendText("Erreur de connexion.");
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty()) return;
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

    private void sendImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = ImageIO.read(chooser.getSelectedFile());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img,"png",baos);
            byte[] data = baos.toByteArray();
            String target = userList.getSelectedValue(); if (target==null) target="ALL";
            out.writeUTF("IMG:" + target + ":" + username);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            appendText("📤 Image envoyée.");
        } catch (Exception e) { appendText("❌ Erreur envoi image."); }
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            byte[] data = new FileInputStream(file).readAllBytes();
            String target = userList.getSelectedValue(); if (target==null) target="ALL";
            out.writeUTF("FILE:" + target + ":" + file.getName());
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            appendText("📤 Fichier envoyé : " + file.getName());
        } catch (Exception e) { appendText("❌ Erreur envoi fichier."); e.printStackTrace(); }
    }

    private class NetworkReceiver implements Runnable {
        public void run() {
            try {
                while (true) {
                    String header = in.readUTF();

                    if (header.startsWith("USERS:")) { updateUserList(header.substring(6)); continue; }

                    if (header.startsWith("IMG_FROM:")) {
                        String sender = header.substring(9);
                        int len = in.readInt(); byte[] data = new byte[len]; in.readFully(data);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        appendText("[Image reçue de " + sender + "]");
                        appendImageInChat(img,sender);
                        continue;
                    }

                    if (header.startsWith("FILE_FROM:")) {
                        String[] p = header.split(":",3);
                        String sender = p[1], fileName = p[2];
                        int len = in.readInt(); byte[] data = new byte[len]; in.readFully(data);
                        File folder = new File("Downloads"); if (!folder.exists()) folder.mkdir();
                        File outFile = new File(folder, fileName);
                        try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(data); }

                        // Lien cliquable dans le chat
                        appendClickableFile(sender, outFile);
                        continue;
                    }

                    if (header.startsWith("PRIV_FROM:")) {
                        String[] p = header.split(":",3);
                        appendText("🔒 Privé de " + p[1] + " : " + p[2]);
                        continue;
                    }

                    if (header.startsWith("PRIV_SENT:")) {
                        String[] p = header.split(":",3);
                        appendText("✔ Privé envoyé à " + p[1]);
                        continue;
                    }

                    appendText(header);
                }
            } catch (Exception e) { appendText("\nDéconnecté du serveur."); }
        }
    }

    private void appendClickableFile(String sender, File file) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, Color.BLUE);
            StyleConstants.setUnderline(attrs,true);
            doc.insertString(doc.getLength(), sender + " a envoyé : " + file.getName() + "\n", attrs);

            chatPane.setCaretPosition(doc.getLength());
            chatPane.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file); }
                    catch (Exception e) { appendText("Impossible d'ouvrir le fichier."); }
                }
            });

        } catch (Exception e) { appendText("Erreur affichage fichier."); }
    }

    private void updateUserList(String list) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : list.split(",")) if (!u.isEmpty() && !u.equals(username)) userListModel.addElement(u);
        });
    }

    public static void main(String[] args) { new chatClient(); }
}
