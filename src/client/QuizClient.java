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
    private JLabel connectionStatusLbl; // New Field

    // UI Colors (Light Theme)
    private final Color CLR_BG = new Color(245, 245, 250); // Light Gray/White
    private final Color CLR_FG = new Color(50, 50, 50); // Dark Text
    private final Color CLR_ACCENT = new Color(0, 114, 188); // Professional Blue

    public QuizClient() {
        setTitle("Distributed Quiz System");
        setSize(900, 700); // Slightly larger for Admin View
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

    private java.util.Properties config;
    private java.util.List<String> serverList = new java.util.ArrayList<>();

    private void connectToServer() {
        new Thread(() -> {
            loadConfig();

            // 1. Try Config First
            boolean connected = tryConnectToKnownServers();

            // 2. If valid config failed, or no config, ASK USER
            if (!connected) {
                // Connection failed for all config entries. Ask User.
                askUserForIP();
            } else {
                // Connected successfully
                updateTopology();
            }
        }).start();
    }

    private boolean tryConnectToKnownServers() {
        for (String serverAddr : serverList) {
            if (attemptConnection(serverAddr)) {
                return true;
            }
        }
        return false;
    }

    private boolean attemptConnection(String serverAddr) {
        try {
            String[] parts = serverAddr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("Trying to connect to " + host + ":" + port + "...");
            Registry registry = LocateRegistry.getRegistry(host, port);

            try {
                service = (QuizService) registry.lookup("QuizService");
                System.out.println("Connected to Leader/Node at " + serverAddr);
                return true;
            } catch (java.rmi.NotBoundException nbe) {
                // If QuizService is not bound, it might be a BACKUP node (Node 2, 3, 4 etc)
                // Let's ask it who the leader is.
                System.out.println("QuizService not found at " + serverAddr + ". Checking if it's a backup node...");

                // We need to guess the Node ID? Or just iterate 1..4?
                // Or better: list the registry to find "Node_X"
                String[] boundNames = registry.list();
                for (String name : boundNames) {
                    if (name.startsWith("Node_")) {
                        common.ElectionService es = (common.ElectionService) registry.lookup(name);
                        int leaderId = es.getCurrentLeaderId();
                        System.out.println("Backup Node says Leader ID is: " + leaderId);

                        if (leaderId != -1) {
                            // Find Leader IP from Topology
                            java.util.List<String> topo = es.getClusterTopology();
                            if (topo != null && leaderId > 0 && leaderId <= topo.size()) {
                                String leaderAddr = topo.get(leaderId - 1); // List is 0-indexed, ID 1-indexed
                                if (!leaderAddr.equals(serverAddr)) {
                                    System.out.println("Redirecting to Leader at: " + leaderAddr);

                                    // RECURSIVE ATTEMPT
                                    // Avoid infinite loop if config is wrong?
                                    // Simple check: don't redirect to self (already checked above)
                                    return attemptConnection(leaderAddr);
                                }
                            }
                        }
                    }
                }
            }

            // Prioritize this server in list for next time (simple optimization)
            // But main logic is in updateTopology
            return false; // Failed if we reach here
        } catch (Exception e) {
            System.err.println("Failed to connect to " + serverAddr + ": " + e.getMessage());
        }
        return false;
    }

    private void updateTopology() {
        // Fetch latest cluster list from the server we just connected to
        new Thread(() -> {
            try {
                java.util.List<String> cluster = service.getClusterTopology();
                if (cluster != null && !cluster.isEmpty()) {
                    System.out.println("Received Cluster Topology: " + cluster);
                    // Update our list, avoiding duplicates but keeping order?
                    // Actually, replace list or merge? For robustness, let's merge.
                    for (String s : cluster) {
                        if (!serverList.contains(s)) {
                            serverList.add(s);
                        }
                    }
                    if (connectionStatusLbl != null) {
                        connectionStatusLbl.setText("Cluster Synced (" + cluster.size() + " nodes)");
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to update topology: " + e.getMessage());
            }
        }).start();
    }

    private void askUserForIP() {
        SwingUtilities.invokeLater(() -> {
            String input = JOptionPane.showInputDialog(this,
                    "Could not connect to default servers.\nPlease enter the Server IP:Port (e.g., 192.168.1.5:1099):",
                    "Connect to Server",
                    JOptionPane.QUESTION_MESSAGE);

            if (input != null && !input.trim().isEmpty()) {
                new Thread(() -> {
                    String cleanInput = input.trim();
                    if (attemptConnection(cleanInput)) {
                        serverList.add(0, cleanInput); // Add to front
                        System.out.println("Connected to manually entered server: " + cleanInput);
                        JOptionPane.showMessageDialog(this, "Connected successfully!");
                        updateTopology(); // SYNC NOW
                    } else {
                        JOptionPane.showMessageDialog(this, "Connection Failed.");
                        // Retry?
                        askUserForIP();
                    }
                }).start();
            } else {
                JOptionPane.showMessageDialog(this, "Exiting application as no server was selected.");
                System.exit(0);
            }
        });
    }

    private void loadConfig() {
        config = new java.util.Properties();
        try (java.io.InputStream input = new java.io.FileInputStream("config.properties")) {
            config.load(input);
            // Collect all vals that look like host:port
            for (String key : config.stringPropertyNames()) {
                if (key.startsWith("node.")) {
                    serverList.add(config.getProperty(key));
                }
            }
        } catch (Exception e) {
            System.err.println("Client could not load config.properties. Will prompt user.");
        }

        // Don't do anything else, connection loop handles empty list
    }

    // ---------------- ROBUST RMI CONNECTION LOGIC ----------------
    private interface RemoteTask<T> {
        T execute() throws Exception;
    }

    private <T> T executeSafe(RemoteTask<T> task) {
        try {
            return task.execute();
        } catch (Exception e) {
            // Check if it's a logic error (App Exception) vs Network Error
            if (!isConnectionError(e)) {
                String msg = e.getMessage();
                // Clean up RMI wrapper noise
                if (msg != null) {
                    if (msg.contains("nested exception is:")) {
                        int index = msg.lastIndexOf("java.rmi.RemoteException:");
                        if (index != -1) {
                            msg = msg.substring(index + "java.rmi.RemoteException:".length()).trim();
                        }
                    }
                    if (msg.contains("Server Exception:")) {
                        msg = msg.replace("Server Exception:", "").trim();
                    }
                }
                final String showMsg = (msg == null || msg.isEmpty()) ? "Unknown Error" : msg;
                SwingUtilities.invokeLater(
                        () -> JOptionPane.showMessageDialog(this, showMsg, "Error", JOptionPane.ERROR_MESSAGE));
                return null;
            }

            System.err.println("RMI Call Failed: " + e.getMessage());
            // Try to reconnect with Failover (Limitless retry? Or One pass?)
            System.out.println("Attempting Failover Reconnect to " + serverList.size() + " nodes...");

            for (String serverAddr : serverList) {
                if (attemptConnection(serverAddr)) {
                    System.out.println("Reconnected to " + serverAddr);
                    try {
                        // Retry the original task
                        return task.execute();
                    } catch (Exception retryEx) {
                        System.err.println("Retry execution failed: " + retryEx.getMessage());
                        // If retry failed with network error, continue loop. Else return null.
                        if (!isConnectionError(retryEx))
                            return null;
                    }
                }
            }

            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Connection Lost to ALL Servers.\nLeader election might be in progress."));
            return null;
        }
    }

    private boolean isConnectionError(Exception e) {
        if (e instanceof java.rmi.ConnectException || e instanceof java.rmi.ConnectIOException
                || e instanceof java.rmi.UnknownHostException || e instanceof java.rmi.NoSuchObjectException) {
            return true;
        }
        String msg = e.getMessage();
        if (msg == null)
            return false;
        msg = msg.toLowerCase();
        return msg.contains("connection refused") || msg.contains("connection reset") || msg.contains("refused to host")
                || msg.contains("lookup");
    }
    // -----------------------------------------------------------

    private JPanel createLoadingPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(CLR_BG);

        JLabel l = new JLabel("Loading...", SwingConstants.CENTER);
        l.setForeground(CLR_FG);
        l.setFont(new Font("Segoe UI", Font.BOLD, 18));
        p.add(l, BorderLayout.CENTER);

        // Add back button for students who might get stuck
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(CLR_BG);
        JButton backBtn = createStyledButton("← Back");
        backBtn.addActionListener(e -> {
            if (currentUser != null && "STUDENT".equalsIgnoreCase(currentUser.getRole())) {
                loadSubjectSelection();
            } else {
                cardLayout.show(mainPanel, "LOGIN");
            }
        });
        buttonPanel.add(backBtn);
        p.add(buttonPanel, BorderLayout.SOUTH);

        return p;
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CLR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Quiz System Login");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(CLR_FG);

        JTextField userField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);
        JButton loginBtn = createStyledButton("Login");

        loginBtn.addActionListener(e -> performLogin(userField.getText(), new String(passField.getPassword())));

        // Connection Status
        String currentServer = serverList.isEmpty() ? "None" : serverList.get(0);
        connectionStatusLbl = new JLabel("Connected to: " + currentServer);
        connectionStatusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        connectionStatusLbl.setForeground(Color.GRAY);

        JButton changeServerBtn = new JButton("Change Server");
        changeServerBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        changeServerBtn.setBackground(Color.LIGHT_GRAY);
        changeServerBtn.addActionListener(e -> askUserForIP());

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

        // Add Connection Info
        gbc.gridy = 4;
        gbc.insets = new Insets(20, 10, 0, 10);
        panel.add(connectionStatusLbl, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(5, 10, 10, 10);
        panel.add(changeServerBtn, gbc);

        return panel;
    }

    private void performLogin(String u, String p) {
        new Thread(() -> {
            User user = executeSafe(() -> service.login(u, p));
            SwingUtilities.invokeLater(() -> {
                if (user != null) {
                    currentUser = user;
                    // Route based on role
                    if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                        loadAdminDashboard();
                    } else if ("REVIEWER".equalsIgnoreCase(user.getRole())) {
                        loadReviewerDashboard();
                    } else if ("TEACHER".equalsIgnoreCase(user.getRole())) {
                        loadCreatorDashboard();
                    } else {
                        // Students always go to subject selection
                        // Per-exam submission check happens when they enter a course code
                        loadSubjectSelection();
                    }
                } else if (service != null) {
                    JOptionPane.showMessageDialog(this, "Invalid Credentials");
                }
            });
        }).start();
    }

    // ---------------- CREATOR DASHBOARD ----------------
    private void loadCreatorDashboard() {
        cardLayout.show(mainPanel, "LOADING");
        SwingUtilities.invokeLater(() -> {
            mainPanel.add(createCreatorPanel(), "CREATOR");
            cardLayout.show(mainPanel, "CREATOR");
        });
    }

    private JPanel createCreatorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(230, 230, 230));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLbl = new JLabel("Exam Creator Dashboard");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLbl.setForeground(Color.DARK_GRAY);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(logoutBtn, BorderLayout.EAST);

        // Create top container for header
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(CLR_BG);
        topContainer.add(header);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(CLR_BG);

        JButton createSubjBtn = createStyledButton("Create New Exam (Draft)");
        JButton addQueBtn = createStyledButton("Add Question to Exam");
        JButton refreshBtn = createStyledButton("Refresh");

        createSubjBtn.addActionListener(e -> showCreateSubjectDialog());
        addQueBtn.addActionListener(e -> showAddQuestionDialog());

        toolbar.add(createSubjBtn);
        toolbar.add(addQueBtn);
        toolbar.add(refreshBtn);

        topContainer.add(toolbar);
        panel.add(topContainer, BorderLayout.NORTH);

        // Table showing creator's exams
        String[] columns = { "ID", "Exam Name", "Code", "Status", "Questions", "Next Step" };
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(220, 220, 220));
        table.getTableHeader().setForeground(Color.BLACK);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(scroll, BorderLayout.CENTER);

        // Refresh logic
        java.awt.event.ActionListener refreshAction = e -> {
            new Thread(() -> {
                try {
                    List<common.Subject> exams = executeSafe(() -> service.getSubjectsByCreator(currentUser.getId()));
                    SwingUtilities.invokeLater(() -> {
                        model.setRowCount(0);
                        if (exams != null) {
                            for (common.Subject exam : exams) {
                                int qCount = executeSafe(() -> service.getQuestionCount(exam.getId()));
                                String nextStep = "";
                                switch (exam.getStatus()) {
                                    case "PENDING_REVIEW":
                                        nextStep = "Waiting for Reviewer approval";
                                        break;
                                    case "APPROVED_FOR_QUESTIONS":
                                        nextStep = "Add questions";
                                        break;
                                    case "QUESTIONS_PENDING":
                                        nextStep = "Waiting for Reviewer to publish";
                                        break;
                                    case "PUBLISHED":
                                        nextStep = "Live for students";
                                        break;
                                    default:
                                        nextStep = exam.getStatus();
                                }
                                model.addRow(new Object[] {
                                        exam.getId(),
                                        exam.getName(),
                                        exam.getAccessCode(),
                                        exam.getStatus(),
                                        qCount,
                                        nextStep
                                });
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        };

        refreshBtn.addActionListener(refreshAction);

        // Initial load
        refreshBtn.doClick();

        return panel;
    }

    private void showCreateSubjectDialog() {
        JTextField nameF = new JTextField();
        JTextField codeF = new JTextField();
        Object[] msg = { "Exam Name:", nameF, "Access Code:", codeF };

        int res = JOptionPane.showConfirmDialog(this, msg, "Create Exam", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            new Thread(() -> {
                try {
                    // Default 2 hours from now
                    java.sql.Timestamp start = new java.sql.Timestamp(System.currentTimeMillis());
                    java.sql.Timestamp end = new java.sql.Timestamp(System.currentTimeMillis() + 7200000); // +2h
                    boolean success = executeSafe(() -> service.addSubject(nameF.getText(), codeF.getText(), start, end,
                            currentUser.getId()));
                    SwingUtilities.invokeLater(() -> {
                        if (success)
                            JOptionPane.showMessageDialog(this, "Draft Exam Created!\nWait for Reviewer to Approve.");
                        else
                            JOptionPane.showMessageDialog(this, "Failed to create exam (Duplicate code?)");
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void showAddQuestionDialog() {
        JTextField subjIdF = new JTextField(); // Ideally a dropdown, but ID/Code is faster to impl
        JTextArea qText = new JTextArea(3, 20);
        JTextField optA = new JTextField();
        JTextField optB = new JTextField();
        JTextField optC = new JTextField();
        JTextField optD = new JTextField();
        JTextField correct = new JTextField("A");

        Object[] msg = {
                "Subject ID (See Admin):", subjIdF,
                "Question:", new JScrollPane(qText),
                "Option A:", optA, "Option B:", optB, "Option C:", optC, "Option D:", optD,
                "Correct (A/B/C/D):", correct
        };

        int res = JOptionPane.showConfirmDialog(this, msg, "Add Question", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            new Thread(() -> {
                try {
                    int sId = Integer.parseInt(subjIdF.getText());
                    boolean success = executeSafe(() -> service.addQuestion(sId, qText.getText(), optA.getText(),
                            optB.getText(), optC.getText(), optD.getText(), correct.getText()));
                    SwingUtilities.invokeLater(() -> {
                        if (success)
                            JOptionPane.showMessageDialog(this, "Question Added!");
                        else
                            JOptionPane.showMessageDialog(this, "Failed to add question.");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()));
                }
            }).start();
        }
    }
    // ---------------------------------------------------

    private void loadSubjectSelection() {
        cardLayout.show(mainPanel, "LOADING");
        SwingUtilities.invokeLater(() -> {
            if (mainPanel.getComponentCount() > 0) {
                // Check if panel already exists or just recreate
            }
            mainPanel.add(createSubjectSelectionPanel(), "SUBJECT_SELECT");
            cardLayout.show(mainPanel, "SUBJECT_SELECT");
        });
    }

    private JPanel createSubjectSelectionPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(CLR_BG);

        // Header with logout button
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CLR_BG);
        header.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> cardLayout.show(this.mainPanel, "LOGIN"));

        header.add(logoutBtn, BorderLayout.EAST);
        mainPanel.add(header, BorderLayout.NORTH);

        // Center panel with form
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CLR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);

        JLabel title = new JLabel("Select Exam Subject");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(CLR_FG);

        JLabel instr = new JLabel("Enter the Access Code provided by your instructor:");
        instr.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        instr.setForeground(Color.GRAY);

        JTextField codeField = new JTextField(15);
        codeField.setFont(new Font("Monospaced", Font.BOLD, 16));
        codeField.setHorizontalAlignment(JTextField.CENTER);

        JButton startBtn = createStyledButton("Start Exam");
        startBtn.setBackground(new Color(46, 204, 113)); // Green

        startBtn.addActionListener(e -> {
            String code = codeField.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please enter a code.");
                return;
            }
            startExamFlow(code);
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(title, gbc);

        gbc.gridy = 1;
        panel.add(instr, gbc);

        gbc.gridy = 2;
        panel.add(codeField, gbc);

        gbc.gridy = 3;
        panel.add(startBtn, gbc);

        mainPanel.add(panel, BorderLayout.CENTER);
        return mainPanel;
    }

    private void startExamFlow(String code) {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            try {
                // 1. Validate Code
                common.Subject subject = executeSafe(() -> service.validateSubjectCode(code, currentUser.getId()));

                if (subject != null) {
                    // 2. Load Questions for this Subject
                    loadQuiz(subject);
                } else {
                    // Validation failed (invalid code or already completed)
                    SwingUtilities.invokeLater(() -> {
                        cardLayout.show(mainPanel, "SUBJECT_SELECT");
                    });
                }
            } catch (Exception e) {
                // Error handled in executeSafe usually, but for logic errors:
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Access Denied",
                            JOptionPane.ERROR_MESSAGE);
                    cardLayout.show(mainPanel, "SUBJECT_SELECT");
                });
            }
        }).start();
    }

    // ---------------- REVIEWER DASHBOARD ----------------
    private void loadReviewerDashboard() {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                mainPanel.add(createReviewerPanel(), "REVIEWER");
                cardLayout.show(mainPanel, "REVIEWER");
            });
        }).start();
    }

    private JPanel createReviewerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(230, 230, 230));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLbl = new JLabel("Exam Reviewer Dashboard");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLbl.setForeground(Color.DARK_GRAY);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(logoutBtn, BorderLayout.EAST);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(CLR_BG);

        JButton refreshBtn = createStyledButton("Refresh");
        toolbar.add(refreshBtn);

        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(CLR_BG);
        topContainer.add(header);
        topContainer.add(toolbar);

        panel.add(topContainer, BorderLayout.NORTH);

        // Table showing pending exams
        String[] columns = { "ID", "Exam Name", "Code", "Status", "Questions", "Next Step" };
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(220, 220, 220));
        table.getTableHeader().setForeground(Color.BLACK);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(scroll, BorderLayout.CENTER);

        // Action Panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        actionPanel.setBackground(CLR_BG);

        JButton approveBtn = createStyledButton("Approve Draft");
        approveBtn.setBackground(new Color(46, 204, 113)); // Green

        JButton viewQuestionsBtn = createStyledButton("View Questions");
        viewQuestionsBtn.setBackground(new Color(52, 152, 219)); // Blue

        JButton publishBtn = createStyledButton("Publish Exam");
        publishBtn.setBackground(new Color(155, 89, 182)); // Purple

        JButton deleteBtn = createStyledButton("Delete Exam");
        deleteBtn.setBackground(new Color(231, 76, 60)); // Red

        actionPanel.add(approveBtn);
        actionPanel.add(viewQuestionsBtn);
        actionPanel.add(publishBtn);
        actionPanel.add(deleteBtn);
        panel.add(actionPanel, BorderLayout.SOUTH);

        // Refresh logic
        java.awt.event.ActionListener refreshAction = e -> {
            new Thread(() -> {
                try {
                    List<common.Subject> exams = executeSafe(() -> service.getPendingExams());
                    SwingUtilities.invokeLater(() -> {
                        model.setRowCount(0);
                        if (exams != null) {
                            for (common.Subject exam : exams) {
                                int qCount = executeSafe(() -> service.getQuestionCount(exam.getId()));
                                String nextStep = "";
                                switch (exam.getStatus()) {
                                    case "PENDING_REVIEW":
                                        nextStep = "Needs Approval";
                                        break;
                                    case "APPROVED_FOR_QUESTIONS":
                                        nextStep = "Creator adding questions";
                                        break;
                                    case "QUESTIONS_PENDING":
                                        nextStep = "Ready to Publish";
                                        break;
                                    case "PUBLISHED":
                                        nextStep = "Live for students";
                                        break;
                                    default:
                                        nextStep = exam.getStatus();
                                }
                                model.addRow(new Object[] {
                                        exam.getId(),
                                        exam.getName(),
                                        exam.getAccessCode(),
                                        exam.getStatus(),
                                        qCount,
                                        nextStep
                                });
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        };

        refreshBtn.addActionListener(refreshAction);

        // Approve Draft Action
        approveBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) model.getValueAt(row, 0);
                String name = (String) model.getValueAt(row, 1);
                String status = (String) model.getValueAt(row, 3);

                if (!status.equals("PENDING_REVIEW")) {
                    JOptionPane.showMessageDialog(panel, "Only exams with PENDING_REVIEW status can be approved.");
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(panel,
                        "Approve exam '" + name + "'?\nCreator will be able to add questions.",
                        "Confirm Approval",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        boolean success = executeSafe(() -> service.approveExamDraft(id));
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                JOptionPane.showMessageDialog(panel, "Exam approved! Creator can now add questions.");
                                refreshBtn.doClick();
                            } else {
                                JOptionPane.showMessageDialog(panel, "Failed to approve exam.");
                            }
                        });
                    }).start();
                }
            } else {
                JOptionPane.showMessageDialog(panel, "Select an exam first.");
            }
        });

        // View Questions Action
        viewQuestionsBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int examId = (int) model.getValueAt(row, 0);
                String examName = (String) model.getValueAt(row, 1);
                new Thread(() -> {
                    List<Question> questions = executeSafe(() -> service.getQuestions(examId));
                    SwingUtilities.invokeLater(() -> showQuestionsDialog(examName, questions));
                }).start();
            } else {
                JOptionPane.showMessageDialog(panel, "Select an exam first.");
            }
        });

        // Publish Exam Action
        publishBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) model.getValueAt(row, 0);
                String name = (String) model.getValueAt(row, 1);
                String status = (String) model.getValueAt(row, 3);
                int qCount = (int) model.getValueAt(row, 4);

                if (!status.equals("QUESTIONS_PENDING")) {
                    JOptionPane.showMessageDialog(panel, "Only exams with QUESTIONS_PENDING status can be published.");
                    return;
                }

                if (qCount == 0) {
                    JOptionPane.showMessageDialog(panel, "Cannot publish exam with no questions!");
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(panel,
                        "Publish exam '" + name + "' with " + qCount
                                + " questions?\nStudents will be able to access it.",
                        "Confirm Publish",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        boolean success = executeSafe(() -> service.publishSubject(id));
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                JOptionPane.showMessageDialog(panel, "Exam published successfully!");
                                refreshBtn.doClick();
                            } else {
                                JOptionPane.showMessageDialog(panel, "Failed to publish exam.");
                            }
                        });
                    }).start();
                }
            } else {
                JOptionPane.showMessageDialog(panel, "Select an exam first.");
            }
        });

        // Delete Exam Action
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) model.getValueAt(row, 0);
                String name = (String) model.getValueAt(row, 1);

                int confirm = JOptionPane.showConfirmDialog(panel,
                        "DELETE exam '" + name
                                + "'?\nThis action cannot be undone!\nAll questions will also be deleted.",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        boolean success = executeSafe(() -> service.deleteSubject(id));
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                JOptionPane.showMessageDialog(panel, "Exam deleted successfully.");
                                refreshBtn.doClick();
                            } else {
                                JOptionPane.showMessageDialog(panel, "Failed to delete exam.");
                            }
                        });
                    }).start();
                }
            } else {
                JOptionPane.showMessageDialog(panel, "Select an exam first.");
            }
        });

        // Initial load
        refreshBtn.doClick();

        return panel;
    }

    // ---------------- ADMIN DASHBOARD (Renamed from Teacher)
    // ----------------
    private void loadAdminDashboard() {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                mainPanel.add(createAdminPanel(), "ADMIN");
                cardLayout.show(mainPanel, "ADMIN");
            });
        }).start();
    }

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CLR_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(230, 230, 230));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLbl = new JLabel("Admin Dashboard");
        titleLbl.setForeground(Color.DARK_GRAY);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(logoutBtn, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // Tab 1: Students
        tabbedPane.addTab("Students", createStudentsTab());

        // Tab 2: Add User
        tabbedPane.addTab("Add User", createAddUserTab());

        panel.add(tabbedPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStudentsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CLR_BG);

        // Optimized Layout
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 10)); // Reduced gap
        toolbar.setBackground(CLR_BG);

        // Exam Selector
        JLabel examLabel = new JLabel("Exam:");
        JComboBox<common.Subject> examSelector = new JComboBox<>();
        examSelector.setPreferredSize(new Dimension(180, 30)); // Slightly smaller

        JCheckBox showSubmittedOnly = new JCheckBox("Show Submitted Only");
        showSubmittedOnly.setBackground(CLR_BG);

        JTextField searchField = new JTextField(10); // Smaller width
        JButton searchBtn = createStyledButton("Search");
        JButton rankBtn = createStyledButton("Rank"); // Shorter text
        JButton refreshBtn = createStyledButton("Refresh");
        JButton allowRetryBtn = createStyledButton("Reset"); // Shorter text
        allowRetryBtn.setBackground(new Color(46, 204, 113)); // Green

        toolbar.add(examLabel);
        toolbar.add(examSelector);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Find:"));
        toolbar.add(searchField);
        toolbar.add(searchBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(showSubmittedOnly); // Add to toolbar
        toolbar.add(rankBtn);
        toolbar.add(allowRetryBtn);
        toolbar.add(refreshBtn);

        panel.add(toolbar, BorderLayout.NORTH);

        // Table
        String[] columns = { "ID", "Student ID", "Name", "Department", "Score", "Submitted?" };
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(220, 220, 220));
        table.getTableHeader().setForeground(Color.BLACK);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new EmptyBorder(0, 10, 10, 10));
        panel.add(scroll, BorderLayout.CENTER);

        // Load Exams into Selector
        new Thread(() -> {
            List<common.Subject> exams = executeSafe(() -> service.getAllSubjects());
            SwingUtilities.invokeLater(() -> {
                if (exams != null) {
                    for (common.Subject exam : exams) {
                        examSelector.addItem(exam);
                    }
                }
            });
        }).start();

        // Logic
        java.awt.event.ActionListener refreshAction = e -> {
            common.Subject selectedExam = (common.Subject) examSelector.getSelectedItem();
            if (selectedExam == null) {
                // Determine if this was an automatic refresh or user interaction
                // For safety, just return silently or log, but user sees "Select Exam"
                return;
            }

            new Thread(() -> {
                try {
                    List<User> students = executeSafe(() -> service.getStudentSubmissionsForExam(selectedExam.getId()));
                    SwingUtilities.invokeLater(() -> {
                        model.setRowCount(0);
                        if (students != null) {
                            String query = searchField.getText().trim().toLowerCase(); // TRIMMED
                            boolean onlySubmitted = showSubmittedOnly.isSelected(); // Checkbox state

                            if (e.getSource() == rankBtn) {
                                students.sort((s1, s2) -> Integer.compare(s2.getScore(), s1.getScore()));
                            } else {
                                students.sort((s1, s2) -> Integer.compare(s1.getId(), s2.getId()));
                            }

                            for (User s : students) {
                                // Filter by Submitted Only
                                if (onlySubmitted && !s.hasSubmitted()) {
                                    continue;
                                }

                                // Null checks for safety
                                String uname = s.getUsername() != null ? s.getUsername().toLowerCase() : "";
                                String fname = s.getFullName() != null ? s.getFullName().toLowerCase() : "";

                                if (!query.isEmpty() && !uname.contains(query) && !fname.contains(query)) {
                                    continue;
                                }
                                model.addRow(new Object[] { s.getId(), s.getUsername(), s.getFullName(),
                                        s.getDepartment(), s.getScore(), s.hasSubmitted() ? "YES" : "NO" });
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        };

        refreshBtn.addActionListener(refreshAction);
        searchBtn.addActionListener(refreshAction);
        searchField.addActionListener(refreshAction); // Enable Enter key
        rankBtn.addActionListener(refreshAction);
        examSelector.addActionListener(refreshAction);
        showSubmittedOnly.addActionListener(refreshAction); // Trigger on checkbox toggle

        allowRetryBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) model.getValueAt(row, 0);
                String name = (String) model.getValueAt(row, 2);

                common.Subject selectedExam = (common.Subject) examSelector.getSelectedItem();
                if (selectedExam == null) {
                    JOptionPane.showMessageDialog(panel, "Please select an exam context first.");
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(panel,
                        "Reset submission for " + name + " in exam '" + selectedExam.getName() + "'?",
                        "Confirm Reset",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        executeSafe(() -> service.resetStudentSubmission(id, selectedExam.getId()));
                        refreshBtn.doClick();
                    }).start();
                }
            } else {
                JOptionPane.showMessageDialog(panel, "Select a student row first.");
            }
        });

        return panel;
    }

    private JPanel createAddUserTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CLR_BG);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("New Teacher", createTeacherForm());
        tabs.addTab("New Reviewer", createReviewerForm());
        tabs.addTab("New Student", createStudentForm());

        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTeacherForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10, 10, 10, 10);
        g.anchor = GridBagConstraints.WEST;

        JTextField userF = new JTextField(20);
        JPasswordField passF = new JPasswordField(20);
        JTextField nameF = new JTextField(20);
        JTextField deptF = new JTextField(20);
        JButton btn = createStyledButton("Create Teacher");

        // UI Helper for rows
        addFormRow(p, g, 0, "Username:", userF);
        addFormRow(p, g, 1, "Password:", passF);
        addFormRow(p, g, 2, "Full Name:", nameF);
        addFormRow(p, g, 3, "Department:", deptF);

        g.gridx = 1;
        g.gridy = 4;
        g.anchor = GridBagConstraints.EAST;
        g.fill = GridBagConstraints.NONE;
        p.add(btn, g);

        btn.addActionListener(e -> {
            String u = userF.getText().trim();
            String pwd = new String(passF.getPassword());
            String n = nameF.getText().trim();
            String d = deptF.getText().trim();
            if (u.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(p, "Username and Password required.");
                return;
            }

            new Thread(() -> {
                boolean ok = executeSafe(() -> service.addTeacher(u, pwd, n, d));
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(p, "Teacher Account Created!");
                        userF.setText("");
                        passF.setText("");
                        nameF.setText("");
                        deptF.setText("");
                    } else {
                        JOptionPane.showMessageDialog(p, "Failed to create account (Username taken?)");
                    }
                });
            }).start();
        });
        return p;
    }

    private JPanel createReviewerForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10, 10, 10, 10);
        g.anchor = GridBagConstraints.WEST;

        JTextField userF = new JTextField(20);
        JPasswordField passF = new JPasswordField(20);
        JTextField nameF = new JTextField(20);
        JButton btn = createStyledButton("Create Reviewer");

        addFormRow(p, g, 0, "Username:", userF);
        addFormRow(p, g, 1, "Password:", passF);
        addFormRow(p, g, 2, "Full Name:", nameF);

        g.gridx = 1;
        g.gridy = 3;
        g.anchor = GridBagConstraints.EAST;
        p.add(btn, g);

        btn.addActionListener(e -> {
            String u = userF.getText().trim();
            String pwd = new String(passF.getPassword());
            String n = nameF.getText().trim();
            if (u.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(p, "Username and Password required.");
                return;
            }

            new Thread(() -> {
                boolean ok = executeSafe(() -> service.addReviewer(u, pwd, n));
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(p, "Reviewer Account Created!");
                        userF.setText("");
                        passF.setText("");
                        nameF.setText("");
                    } else {
                        JOptionPane.showMessageDialog(p, "Failed to create account (Username taken?)");
                    }
                });
            }).start();
        });
        return p;
    }

    private JPanel createStudentForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10, 10, 10, 10);
        g.anchor = GridBagConstraints.WEST;

        JTextField userF = new JTextField(20);
        JPasswordField passF = new JPasswordField(20);
        JTextField nameF = new JTextField(20);
        JTextField deptF = new JTextField(20);
        String[] genders = { "Male", "Female" };
        JComboBox<String> genderBox = new JComboBox<>(genders);

        JButton btn = createStyledButton("Create Student");

        addFormRow(p, g, 0, "Student ID (User):", userF);
        addFormRow(p, g, 1, "Password:", passF);
        addFormRow(p, g, 2, "Full Name:", nameF);
        addFormRow(p, g, 3, "Department:", deptF);

        g.gridx = 0;
        g.gridy = 4;
        JLabel l = new JLabel("Gender:");
        l.setForeground(CLR_FG);
        p.add(l, g);
        g.gridx = 1;
        p.add(genderBox, g);

        g.gridx = 1;
        g.gridy = 5;
        g.anchor = GridBagConstraints.EAST;
        p.add(btn, g);

        btn.addActionListener(e -> {
            String u = userF.getText().trim();
            String pwd = new String(passF.getPassword());
            String n = nameF.getText().trim();
            String d = deptF.getText().trim();
            String gen = (String) genderBox.getSelectedItem();

            if (u.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(p, "Username and Password required.");
                return;
            }

            new Thread(() -> {
                boolean ok = executeSafe(() -> service.addStudent(u, pwd, n, d, gen));
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(p, "Student Account Created!");
                        userF.setText("");
                        passF.setText("");
                        nameF.setText("");
                        deptF.setText("");
                    } else {
                        JOptionPane.showMessageDialog(p, "Failed to create account (ID taken?)");
                    }
                });
            }).start();
        });
        return p;
    }

    private void addFormRow(JPanel p, GridBagConstraints g, int row, String label, JComponent comp) {
        g.gridx = 0;
        g.gridy = row;
        JLabel l = new JLabel(label);
        l.setForeground(CLR_FG);
        p.add(l, g);
        g.gridx = 1;
        p.add(comp, g);
    }
    // ---------------------------------------------------

    private void loadQuiz(common.Subject subject) {
        cardLayout.show(mainPanel, "LOADING");
        new Thread(() -> {
            List<Question> questions = executeSafe(() -> service.getQuestions(subject.getId()));

            // Then Get Shuffle Logic (Code Migration)
            common.ShuffleStrategy shuffler = executeSafe(() -> service.getShuffleStrategy());

            if (questions != null) {
                if (shuffler != null) {
                    System.out.println("Applying Migrated Shuffle Logic...");
                    shuffler.shuffle(questions);
                }

                SwingUtilities.invokeLater(() -> {
                    mainPanel.add(createQuizPanel(questions, subject), "QUIZ");
                    cardLayout.show(mainPanel, "QUIZ");
                });
            }
        }).start();
    }

    private JPanel createQuizPanel(List<Question> questions, common.Subject subject) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLR_BG);

        JPanel content = new JPanel();

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLR_BG);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        Map<Integer, ButtonGroup> answerGroups = new HashMap<>();

        for (Question q : questions) {
            JPanel qPanel = new JPanel(new BorderLayout());
            qPanel.setBackground(Color.WHITE); // Light card
            qPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                    new EmptyBorder(10, 10, 10, 10)));
            qPanel.setMaximumSize(new Dimension(700, 150));

            JLabel qText = new JLabel("Q: " + q.getText());
            qText.setForeground(CLR_FG);
            qText.setFont(new Font("Segoe UI", Font.BOLD, 14));
            qPanel.add(qText, BorderLayout.NORTH);

            JPanel optionsPanel = new JPanel(new GridLayout(2, 2));
            optionsPanel.setBackground(Color.WHITE);

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
        JLabel titleLbl = new JLabel("  " + subject.getName() + " - " + currentUser.getUsername());
        titleLbl.setForeground(CLR_FG);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JLabel timeLbl = new JLabel("Syncing Time...  ");
        timeLbl.setForeground(new Color(230, 126, 34)); // Orange
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
        submitBtn.addActionListener(e -> submitAnswers(answerGroups, subject.getId()));
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(CLR_BG);
        btnPanel.add(submitBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void submitAnswers(Map<Integer, ButtonGroup> groups, int subjectId) {
        Map<Integer, String> answers = new HashMap<>();
        for (Map.Entry<Integer, ButtonGroup> entry : groups.entrySet()) {
            ButtonModel bm = entry.getValue().getSelection();
            if (bm != null) {
                answers.put(entry.getKey(), bm.getActionCommand());
            }
        }

        new Thread(() -> {
            Integer score = executeSafe(() -> service.submitMockQuiz(currentUser.getId(), subjectId, answers));
            SwingUtilities.invokeLater(() -> {
                if (score != null) {
                    JOptionPane.showMessageDialog(this, "Quiz Completed!\nYour Score: " + score);
                    cardLayout.show(mainPanel, "LOGIN"); // Go back to start
                }
            });
        }).start();
    }

    // Admin Question Viewer
    private void showQuestionsDialog(String examName, List<Question> questions) {
        JDialog dialog = new JDialog(this, "Questions for: " + examName, true);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(CLR_BG);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel titleLbl = new JLabel("Review Questions (" + questions.size() + " total)");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLbl.setForeground(CLR_FG);
        mainPanel.add(titleLbl, BorderLayout.NORTH);

        // Questions list
        JPanel questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        questionsPanel.setBackground(Color.WHITE);

        int qNum = 1;
        for (Question q : questions) {
            JPanel qPanel = new JPanel();
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBackground(Color.WHITE);
            qPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                    new EmptyBorder(10, 10, 10, 10)));
            qPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel qText = new JLabel("<html><b>Q" + qNum + ":</b> " + q.getText() + "</html>");
            qText.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            qPanel.add(qText);

            qPanel.add(Box.createVerticalStrut(8));

            JLabel optA = new JLabel("  A) " + q.getOptionA());
            JLabel optB = new JLabel("  B) " + q.getOptionB());
            JLabel optC = new JLabel("  C) " + q.getOptionC());
            JLabel optD = new JLabel("  D) " + q.getOptionD());

            optA.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            optB.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            optC.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            optD.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            qPanel.add(optA);
            qPanel.add(optB);
            qPanel.add(optC);
            qPanel.add(optD);

            questionsPanel.add(qPanel);
            questionsPanel.add(Box.createVerticalStrut(10));
            qNum++;
        }

        JScrollPane scroll = new JScrollPane(questionsPanel);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(scroll, BorderLayout.CENTER);

        JButton closeBtn = createStyledButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setBackground(CLR_BG);
        btnPanel.add(closeBtn);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    // UI Helpers
    private JRadioButton createRadio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setForeground(CLR_FG);
        r.setBackground(Color.WHITE);
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
