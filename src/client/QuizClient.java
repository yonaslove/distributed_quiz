package client;

import common.Question;
import common.QuizService;
import common.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizClient extends JFrame {
    private QuizService service;
    private User currentUser;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // UI Colors
    private final Color CLR_BG = new Color(30, 30, 30);
    private final Color CLR_FG = new Color(230, 230, 230);
    private final Color CLR_ACCENT = new Color(0, 122, 204);

    public QuizClient() {
        setTitle("Distributed Quiz System");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Setup UI
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(CLR_BG);

        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createLoadingPanel(), "LOADING");

        add(mainPanel);

        // Try to connect to RMI
        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                // Ask user for Server IP (Default to localhost)
                String serverIp = JOptionPane.showInputDialog(this, "Enter Server IP Address:", "localhost");
                if (serverIp == null || serverIp.trim().isEmpty()) {
                    serverIp = "localhost";
                }

                Registry registry = LocateRegistry.getRegistry(serverIp, 1099);
                service = (QuizService) registry.lookup("QuizService");
            } catch (Exception e) {
                SwingUtilities.invokeLater(
                        () -> JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage()));
            }
        }).start();
    }

    // ---------------- ROBUST RMI CONNECTION LOGIC ----------------
    private interface RemoteTask<T> {
        T execute() throws Exception;
    }

    private <T> T executeSafe(RemoteTask<T> task) {
        try {
            return task.execute();
        } catch (Exception e) {
            System.err.println("RMI Call Failed: " + e.getMessage());
            // Try to reconnect
            try {
                System.out.println("Attempting to reconnect to QuizService...");
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                service = (QuizService) registry.lookup("QuizService");
                System.out.println("Reconnected!");
                return task.execute(); // Retry once
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Connection Lost to Server.\nLeader election might be in progress.\nPlease wait a moment and try again."));
                return null;
            }
        }
    }
    // -----------------------------------------------------------

    private JPanel createLoadingPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(CLR_BG);
        JLabel l = new JLabel("Loading...", SwingConstants.CENTER);
        l.setForeground(CLR_FG);
        p.add(l);
        return p;
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CLR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Student Login");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(CLR_FG);

        JTextField userField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);
        JButton loginBtn = createStyledButton("Login");

        loginBtn.addActionListener(e -> performLogin(userField.getText(), new String(passField.getPassword())));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        JLabel l1 = new JLabel("Username:");
        l1.setForeground(CLR_FG);
        panel.add(l1, gbc);
        gbc.gridx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel l2 = new JLabel("Password:");
        l2.setForeground(CLR_FG);
        panel.add(l2, gbc);
        gbc.gridx = 1;
        panel.add(passField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(loginBtn, gbc);

        return panel;
    }

    private void performLogin(String u, String p) {
        new Thread(() -> {
            User user = executeSafe(() -> service.login(u, p));
            SwingUtilities.invokeLater(() -> {
                if (user != null) {
                    currentUser = user;
                    if ("TEACHER".equalsIgnoreCase(user.getRole())) {
                        loadTeacherDashboard();
                    } else {
                        loadQuiz();
                    }
                } else if (service != null) {
                    JOptionPane.showMessageDialog(this, "Invalid Credentials");
                }
            });
        }).start();
    }

    // ---------------- TEACHER DASHBOARD ----------------
    private void loadTeacherDashboard() {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                mainPanel.add(createTeacherPanel(), "TEACHER");
                cardLayout.show(mainPanel, "TEACHER");
            });
        }).start();
    }

    private JPanel createTeacherPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CLR_BG);
        JLabel titleLbl = new JLabel("  Admin Dashboard - " + currentUser.getUsername());
        titleLbl.setForeground(CLR_FG);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(titleLbl, BorderLayout.WEST);

        panel.add(header, BorderLayout.NORTH);

        // Content (Log Area)
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(40, 40, 40));
        logArea.setForeground(Color.CYAN);
        JScrollPane scroll = new JScrollPane(logArea);
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(CLR_BG);

        JButton refreshBtn = createStyledButton("Refresh Logs");
        refreshBtn.addActionListener(e -> {
            new Thread(() -> {
                java.util.List<String> logs = executeSafe(() -> service.getAllResults());
                SwingUtilities.invokeLater(() -> {
                    logArea.setText("");
                    if (logs != null) {
                        for (String line : logs)
                            logArea.append(line + "\n");
                    }
                });
            }).start();
        });

        JButton logoutBtn = createStyledButton("Logout");
        logoutBtn.setBackground(Color.RED.darker());
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        btnPanel.add(refreshBtn);
        btnPanel.add(logoutBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        // Initial Load
        refreshBtn.doClick();

        return panel;
    }
    // ---------------------------------------------------

    private void loadQuiz() {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            List<Question> questions = executeSafe(() -> service.getQuestions());

            // Then Get Shuffle Logic (Code Migration)
            common.ShuffleStrategy shuffler = executeSafe(() -> service.getShuffleStrategy());

            if (questions != null) {
                if (shuffler != null) {
                    System.out.println("Applying Migrated Shuffle Logic...");
                    shuffler.shuffle(questions);
                }

                SwingUtilities.invokeLater(() -> {
                    mainPanel.add(createQuizPanel(questions), "QUIZ");
                    cardLayout.show(mainPanel, "QUIZ");
                });
            }
        }).start();
    }

    private JPanel createQuizPanel(List<Question> questions) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_BG);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLR_BG);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        Map<Integer, ButtonGroup> answerGroups = new HashMap<>();

        for (Question q : questions) {
            JPanel qPanel = new JPanel(new BorderLayout());
            qPanel.setBackground(new Color(45, 45, 45));
            qPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            qPanel.setMaximumSize(new Dimension(700, 150));

            JLabel qText = new JLabel("Q: " + q.getText());
            qText.setForeground(Color.WHITE);
            qText.setFont(new Font("Segoe UI", Font.BOLD, 14));
            qPanel.add(qText, BorderLayout.NORTH);

            JPanel optionsPanel = new JPanel(new GridLayout(2, 2));
            optionsPanel.setBackground(new Color(45, 45, 45));

            JRadioButton rA = createRadio(q.getOptionA());
            JRadioButton rB = createRadio(q.getOptionB());
            JRadioButton rC = createRadio(q.getOptionC());
            JRadioButton rD = createRadio(q.getOptionD());

            rA.setActionCommand("A");
            rB.setActionCommand("B");
            rC.setActionCommand("C");
            rD.setActionCommand("D");

            ButtonGroup bg = new ButtonGroup();
            bg.add(rA);
            bg.add(rB);
            bg.add(rC);
            bg.add(rD);
            answerGroups.put(q.getId(), bg);

            optionsPanel.add(rA);
            optionsPanel.add(rB);
            optionsPanel.add(rC);
            optionsPanel.add(rD);
            qPanel.add(optionsPanel, BorderLayout.CENTER);

            content.add(qPanel);
            content.add(Box.createVerticalStrut(15));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        // Header with Clock
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CLR_BG);
        JLabel titleLbl = new JLabel("  Quiz Session - " + currentUser.getUsername());
        titleLbl.setForeground(CLR_FG);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JLabel timeLbl = new JLabel("Syncing Time...  ");
        timeLbl.setForeground(Color.ORANGE);
        timeLbl.setFont(new Font("Consolas", Font.BOLD, 14));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(timeLbl, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Start Clock Sync
        new Thread(() -> {
            try {
                // Initial sync safely
                Long serverTimeObj = executeSafe(() -> service.getServerTime());
                if (serverTimeObj == null)
                    return;

                long serverTime = serverTimeObj;
                long localTime = System.currentTimeMillis();
                long offset = serverTime - localTime;

                SwingUtilities.invokeLater(() -> {
                    new Timer(1000, e -> {
                        long currentServerTime = System.currentTimeMillis() + offset;
                        timeLbl.setText(
                                new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(currentServerTime))
                                        + "  ");
                    }).start();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        JButton submitBtn = createStyledButton("Submit Answers");
        submitBtn.addActionListener(e -> submitAnswers(answerGroups));
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(CLR_BG);
        btnPanel.add(submitBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void submitAnswers(Map<Integer, ButtonGroup> groups) {
        Map<Integer, String> answers = new HashMap<>();
        for (Map.Entry<Integer, ButtonGroup> entry : groups.entrySet()) {
            ButtonModel bm = entry.getValue().getSelection();
            if (bm != null) {
                answers.put(entry.getKey(), bm.getActionCommand());
            }
        }

        new Thread(() -> {
            Integer score = executeSafe(() -> service.submitMockQuiz(currentUser.getId(), answers));
            SwingUtilities.invokeLater(() -> {
                if (score != null) {
                    JOptionPane.showMessageDialog(this, "Quiz Completed!\nYour Score: " + score);
                    cardLayout.show(mainPanel, "LOGIN"); // Go back to start
                }
            });
        }).start();
    }

    // UI Helpers
    private JRadioButton createRadio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setForeground(CLR_FG);
        r.setBackground(new Color(45, 45, 45));
        r.setFocusPainted(false);
        return r;
    }

    private JButton createStyledButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(CLR_ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setBorder(new EmptyBorder(10, 20, 10, 20));
        return b;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new QuizClient().setVisible(true));
    }
}
