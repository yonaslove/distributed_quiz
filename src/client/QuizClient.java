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

            boolean connected = false;
            for (String serverAddr : serverList) {
                try {
                    String[] parts = serverAddr.split(":");
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);

                    System.out.println("Trying to connect to " + host + ":" + port + "...");
                    Registry registry = LocateRegistry.getRegistry(host, port);
                    service = (QuizService) registry.lookup("QuizService");
                    System.out.println("Connected to Leader/Node at " + serverAddr);
                    connected = true;
                    break;
                } catch (Exception e) {
                    System.err.println("Failed to connect to " + serverAddr);
                }
            }

            if (!connected) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Could not connect to ANY server in the cluster.\nPlease check config.properties."));
            }
        }).start();
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
            System.err.println("Client could not load config.properties. Defaulting to localhost:1099");
            serverList.add("localhost:1099");
        }
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
            // Try to reconnect with Failover
            System.out.println("Attempting Failover Reconnect...");

            for (String serverAddr : serverList) {
                try {
                    String[] parts = serverAddr.split(":");
                    Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
                    service = (QuizService) registry.lookup("QuizService");
                    System.out.println("Reconnected to " + serverAddr);
                    return task.execute(); // Retry success
                } catch (Exception ex) {
                }
            }

            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Connection Lost to ALL Servers.\nLeader election might be in progress."));
            return null;
        }
    }

    private boolean isConnectionError(Exception e) {
        if (e instanceof java.rmi.ConnectException || e instanceof java.rmi.ConnectIOException
                || e instanceof java.rmi.UnknownHostException) {
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
        p.add(l);
        return p;
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CLR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("Student Login");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
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
                    } else if ("CREATOR".equalsIgnoreCase(user.getRole())) {
                        loadCreatorDashboard();
                    } else if (user.hasSubmitted()) {
                        JOptionPane.showMessageDialog(this,
                                "You have already submitted this quiz.\nContact Admin to retake.");
                    } else {
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
        panel.add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(CLR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JButton createSubjBtn = createStyledButton("Create New Exam (Draft)");
        JButton addQueBtn = createStyledButton("Add Question to Exam");

        createSubjBtn.addActionListener(e -> showCreateSubjectDialog());
        addQueBtn.addActionListener(e -> showAddQuestionDialog());

        gbc.gridx = 0;
        gbc.gridy = 0;
        content.add(createSubjBtn, gbc);

        gbc.gridy = 1;
        content.add(addQueBtn, gbc);

        panel.add(content, BorderLayout.CENTER);
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
                            JOptionPane.showMessageDialog(this, "Draft Exam Created!\nWait for Admin to Publish.");
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

        return panel;
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

    // ---------------- TEACHER DASHBOARD (Fixed Layout & Light Theme)
    // ----------------
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
        JPanel panel = new JPanel(new BorderLayout(10, 10)); // Add gap
        panel.setBackground(CLR_BG);

        // 1. TOP CONTAINER (Header + Toolbar)
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(CLR_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(230, 230, 230)); // Light Grey Header
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
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); // Flow with gap
        toolbar.setBackground(CLR_BG);

        JTextField searchField = new JTextField(15);
        JButton searchBtn = createStyledButton("Search");
        JButton rankBtn = createStyledButton("Rank Score");
        JButton refreshBtn = createStyledButton("Refresh");
        JButton allowRetryBtn = createStyledButton("Reset Student");
        allowRetryBtn.setBackground(new Color(46, 204, 113)); // Green

        toolbar.add(new JLabel("Find:"));
        toolbar.add(searchField);
        toolbar.add(searchBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(rankBtn);
        toolbar.add(refreshBtn);
        toolbar.add(allowRetryBtn);

        JButton publishBtn = createStyledButton("Publish Exam");
        publishBtn.setBackground(new Color(155, 89, 182)); // Purple
        publishBtn.addActionListener(e -> {
            String sIdStr = JOptionPane.showInputDialog(panel, "Enter Subject ID to Publish:");
            if (sIdStr != null && !sIdStr.trim().isEmpty()) {
                new Thread(() -> {
                    boolean success = executeSafe(() -> service.publishSubject(Integer.parseInt(sIdStr.trim())));
                    SwingUtilities.invokeLater(() -> {
                        if (success)
                            JOptionPane.showMessageDialog(panel, "Exam Published Successfully!");
                        else
                            JOptionPane.showMessageDialog(panel, "Failed to Publish Exam (Invalid ID?)");
                    });
                }).start();
            }
        });
        toolbar.add(publishBtn);

        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        topContainer.add(header);
        topContainer.add(toolbar);
        panel.add(topContainer, BorderLayout.NORTH);

        // 3. Table
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

        // Logic
        java.awt.event.ActionListener refreshAction = e -> {
            new Thread(() -> {
                try {
                    List<User> students = executeSafe(() -> service.getAllStudents());
                    SwingUtilities.invokeLater(() -> {
                        model.setRowCount(0);
                        if (students != null) {
                            String query = searchField.getText().toLowerCase();

                            if (e.getSource() == rankBtn) {
                                students.sort((s1, s2) -> Integer.compare(s2.getScore(), s1.getScore()));
                            } else {
                                students.sort((s1, s2) -> Integer.compare(s1.getId(), s2.getId()));
                            }

                            for (User s : students) {
                                if (!query.isEmpty() && !s.getUsername().toLowerCase().contains(query)
                                        && !s.getFullName().toLowerCase().contains(query)) {
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
        rankBtn.addActionListener(refreshAction);

        allowRetryBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) model.getValueAt(row, 0);
                String name = (String) model.getValueAt(row, 2);
                int confirm = JOptionPane.showConfirmDialog(panel, "Reset quiz for " + name + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        executeSafe(() -> service.resetStudentSubmission(id));
                        refreshBtn.doClick();
                    }).start();
                }
            } else {
                JOptionPane.showMessageDialog(panel, "Select a student row first.");
            }
        });

        // Initial Load
        refreshBtn.doClick();
        return panel;
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
