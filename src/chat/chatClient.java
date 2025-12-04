package chat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.awt.Desktop;
import javax.sound.sampled.*;

public class chatClient extends JFrame {

    private JTextPane chatPane;
    private StyledDocument doc;
    private JTextField messageField;
    private JButton sendButton, imageButton, fileButton, audioButton, recordButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    private boolean isRecording = false;

    public chatClient() {
        username = JOptionPane.showInputDialog(this, "Entrez votre nom :");
        if (username == null || username.trim().isEmpty()) username = "Client";

        setTitle("Chat Client - " + username);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        doc = chatPane.getStyledDocument();
        JScrollPane chatScroll = new JScrollPane(chatPane);

        messageField = new JTextField();
        sendButton = new JButton("Envoyer");
        imageButton = new JButton("📷 Image");
        fileButton = new JButton("📁 Fichier");
        audioButton = new JButton("🎵 Audio");
        recordButton = new JButton("🎙️ Enregistrer");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 5));
        buttonsPanel.add(sendButton);
        buttonsPanel.add(imageButton);
        buttonsPanel.add(fileButton);
        buttonsPanel.add(audioButton);
        buttonsPanel.add(recordButton);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));

        add(chatScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        add(userScroll, BorderLayout.EAST);

        connectToServer("127.0.0.1", 6000);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        imageButton.addActionListener(e -> sendImage());
        fileButton.addActionListener(e -> sendFile());
        audioButton.addActionListener(e -> sendAudio());

        recordButton.addActionListener(e -> {
            if (!isRecording) {
                isRecording = true;
                recordButton.setText("⏹️ Stop");
                new Thread(this::startRecording).start();
            } else {
                isRecording = false;
                recordButton.setText("🎙️ Enregistrer");
            }
        });

        new Thread(new NetworkReceiver()).start();
        setVisible(true);
    }

    private void appendText(String txt) {
        try { doc.insertString(doc.getLength(), txt + "\n", null); chatPane.setCaretPosition(doc.getLength()); }
        catch (Exception e) {}
    }

    private void appendImageInChat(String sender, BufferedImage img) {
        try {
            doc.insertString(doc.getLength(), sender + " :\n", null);
            ImageIcon icon = new ImageIcon(img.getScaledInstance(250, -1, Image.SCALE_SMOOTH));
            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(icon);
            doc.insertString(doc.getLength(), "\n\n", null);
        } catch (Exception e) {}
    }

    private void connectToServer(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(username);
            appendText("Connecté au serveur.");
        } catch (Exception e) { appendText("❌ Erreur de connexion."); }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty()) return;
        try {
            String target = userList.getSelectedValue();
            if (target == null || target.equals("Tous")) {
                out.writeUTF(msg);
                appendText("Moi : " + msg);
            } else {
                out.writeUTF("PRIV:" + target + ":" + msg);
                appendText("Moi → (privé à " + target + "): " + msg);
            }
        } catch (Exception e) {}
        messageField.setText("");
    }

    private void sendImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            BufferedImage img = ImageIO.read(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] data = baos.toByteArray();

            String target = userList.getSelectedValue();
            if (target == null || target.equals("Tous")) target = "ALL";

            out.writeUTF("IMG:" + target + ":" + username);
            out.writeInt(data.length);
            out.write(data);
            out.flush();

            appendText("[Image envoyée]");
            appendImageInChat("Moi", img);
        } catch (Exception e) { appendText("❌ Erreur envoi image."); }
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            byte[] data = new FileInputStream(file).readAllBytes();

            String target = userList.getSelectedValue();
            if (target == null || target.equals("Tous")) target = "ALL";

            out.writeUTF("FILE:" + target + ":" + file.getName());
            out.writeInt(data.length);
            out.write(data);
            out.flush();

            appendText("📤 Fichier envoyé : " + file.getName());
            appendFileButtons("Moi", file);
        } catch (Exception e) { appendText("❌ Erreur envoi fichier."); }
    }

    private void sendAudio() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            File audio = chooser.getSelectedFile();
            byte[] data = new FileInputStream(audio).readAllBytes();

            String target = userList.getSelectedValue();
            if (target == null || target.equals("Tous")) target = "ALL";

            out.writeUTF("AUDIO:" + target + ":" + audio.getName());
            out.writeInt(data.length);
            out.write(data);
            out.flush();

            appendText("🎵 Audio envoyé : " + audio.getName());
            appendFileButtons("Moi", audio);
        } catch (Exception e) { appendText("❌ Erreur envoi audio."); }
    }

    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            appendText("🔴 Enregistrement en cours...");

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            while (isRecording) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) outStream.write(buffer, 0, count);
            }

            line.stop();
            line.close();
            appendText("⏹️ Enregistrement terminé.");

            byte[] audioBytes = outStream.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream ais = new AudioInputStream(bais, format, audioBytes.length / format.getFrameSize());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, baos);
            byte[] wavData = baos.toByteArray();

            String audioName = "Enregistrement_" + System.currentTimeMillis() + ".wav";
            String target = userList.getSelectedValue();
            if (target == null || target.equals("Tous")) target = "ALL";

            out.writeUTF("AUDIO:" + target + ":" + audioName);
            out.writeInt(wavData.length);
            out.write(wavData);
            out.flush();

            appendText("🎵 Message vocal envoyé !");
            // créer fichier temporaire pour bouton ouvrir/télécharger
            File tempFile = new File("Downloads", audioName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) { fos.write(wavData); }
            appendFileButtons("Moi", tempFile);

        } catch (Exception ex) {
            appendText("❌ Erreur enregistrement vocal : " + ex.getMessage());
            isRecording = false;
            SwingUtilities.invokeLater(() -> recordButton.setText("🎙️ Enregistrer"));
        }
    }

    private void appendFileButtons(String sender, File file) {
        try {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.add(new JLabel(sender + " a envoyé : " + file.getName()));

            JButton openBtn = new JButton("Ouvrir");
            JButton saveBtn = new JButton("Télécharger");

            openBtn.addActionListener(e -> {
                try { Desktop.getDesktop().open(file); }
                catch (Exception ex) { appendText("Erreur ouverture fichier."); }
            });

            saveBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(file.getName()));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try { Files.copy(file.toPath(), chooser.getSelectedFile().toPath(), StandardCopyOption.REPLACE_EXISTING); }
                    catch (Exception ex) { appendText("Erreur téléchargement."); }
                }
            });

            panel.add(openBtn);
            panel.add(saveBtn);

            chatPane.insertComponent(panel);
            doc.insertString(doc.getLength(), "\n\n", null);
            chatPane.setCaretPosition(doc.getLength());

        } catch (Exception e) { appendText("Erreur affichage fichier."); }
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
                        int size = in.readInt();
                        byte[] data = new byte[size];
                        in.readFully(data);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        appendText("[Image reçue de " + sender + "]");
                        appendImageInChat(sender, img);
                        continue;
                    }

                    if (header.startsWith("FILE_FROM:")) {
                        String[] p = header.split(":", 3);
                        String sender = p[1];
                        String fileName = p[2];
                        int size = in.readInt();
                        byte[] bytes = new byte[size];
                        in.readFully(bytes);

                        File receivedDir = new File("Downloads");
                        if (!receivedDir.exists()) receivedDir.mkdir();
                        File file = new File(receivedDir, fileName);
                        try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(bytes); }

                        appendText("[Fichier reçu de " + sender + "]");
                        appendFileButtons(sender, file);
                        continue;
                    }

                    if (header.startsWith("AUDIO_FROM:")) {
                        String[] p = header.split(":", 3);
                        String sender = p[1];
                        String audioName = p[2];

                        int size = in.readInt();
                        byte[] bytes = new byte[size];
                        in.readFully(bytes);

                        File receivedDir = new File("Downloads");
                        if (!receivedDir.exists()) receivedDir.mkdir();
                        File file = new File(receivedDir, audioName);
                        try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(bytes); }

                        appendText("[Audio reçu de " + sender + "]");
                        appendFileButtons(sender, file);
                        continue;
                    }

                    appendText(header);
                }
            } catch (Exception e) { appendText("Déconnecté du serveur."); }
        }
    }

    private void updateUserList(String list) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            userListModel.addElement("Tous"); // broadcast
            for (String u : list.split(",")) {
                if (!u.isEmpty() && !u.equals(username))
                    userListModel.addElement(u);
            }
        });
    }

    public static void main(String[] args) { new chatClient(); }
}
