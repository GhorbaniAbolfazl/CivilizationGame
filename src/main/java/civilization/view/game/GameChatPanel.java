package civilization.view.game;

import civilization.protocol.ChatMessagePush;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.function.Consumer;

// PHASE 3 - Added in Phase 3
public final class GameChatPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final JTextArea messages = new JTextArea();
    // PHASE 3 - Added in Phase 3
    private final JTextField input = new JTextField();
    // PHASE 3 - Added in Phase 3
    private final JButton send = new JButton("Send");
    // PHASE 3 - Added in Phase 3
    private Consumer<String> sendAction = ignored -> {};

    // PHASE 3 - Added in Phase 3
    public GameChatPanel() {
        super(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Public game chat"));
        messages.setEditable(false);
        messages.setLineWrap(true);
        messages.setWrapStyleWord(true);
        add(new JScrollPane(messages), BorderLayout.CENTER);
        JPanel entry = new JPanel(new BorderLayout(5, 0));
        entry.add(input, BorderLayout.CENTER);
        entry.add(send, BorderLayout.EAST);
        add(entry, BorderLayout.SOUTH);
        // PHASE 3 - Added in Phase 3
        Runnable submit = () -> {
            String content = input.getText().trim();
            if (content.isEmpty()) return;
            sendAction.accept(content);
            input.setText("");
        };
        send.addActionListener(event -> submit.run());
        input.addActionListener(event -> submit.run());
    }

    // PHASE 3 - Added in Phase 3
    public void setSendAction(Consumer<String> sendAction) {
        this.sendAction = Objects.requireNonNull(sendAction, "sendAction");
    }

    // PHASE 3 - Added in Phase 3
    public void append(ChatMessagePush message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Game chat UI updates must run on EDT");
        }
        messages.append("[" + message.createdAt() + "] " + message.senderUsername()
                + ": " + message.content() + System.lineSeparator());
        messages.setCaretPosition(messages.getDocument().getLength());
    }

    // PHASE 3 - Added in Phase 3
    public String messageText() {
        return messages.getText();
    }

    // PHASE 3 - Added in Phase 3
    public JTextField input() {
        return input;
    }

    // PHASE 3 - Added in Phase 3
    public JButton sendButton() {
        return send;
    }

    // PHASE 3 - Added in Phase 3
    public void setNetworkAvailable(boolean available) {
        input.setEnabled(available); send.setEnabled(available);
        send.setToolTipText(available ? "Send public chat through the authoritative server"
                : "Reconnect before sending chat");
    }
}
