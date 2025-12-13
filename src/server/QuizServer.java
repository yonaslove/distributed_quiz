package server;

import common.ElectionService;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.TimerTask;

public class QuizServer extends UnicastRemoteObject implements ElectionService {
    private int nodeId;
    private int currentLeaderId = -1;
    private boolean isCoordinator = false;

    // Config
    private java.util.Properties config;
    private java.util.List<Integer> allNodes = new java.util.ArrayList<>();
    private int myPort = 1099; // Default

    private QuizServiceImpl quizService;
    private Registry registry;

    public QuizServer(int id) throws RemoteException {
        super();
        this.nodeId = id;
        loadConfig();
        this.quizService = new QuizServiceImpl(this);

        System.out.println("Server Node " + params(nodeId) + " started on Port " + myPort);

        startHeartbeat();
    }

    private void loadConfig() {
        config = new java.util.Properties();
        try (java.io.InputStream input = new java.io.FileInputStream("config.properties")) {
            config.load(input);
            for (String key : config.stringPropertyNames()) {
                if (key.startsWith("node.")) {
                    int nId = Integer.parseInt(key.split("\\.")[1]);
                    allNodes.add(nId);
                    if (nId == this.nodeId) {
                        String[] parts = config.getProperty(key).split(":");
                        this.myPort = Integer.parseInt(parts[1]);
                    }
                }
            }
            java.util.Collections.sort(allNodes);
        } catch (Exception e) {
            System.err.println("Could not load config.properties. Using Default.");
            allNodes.add(1);
            allNodes.add(2);
        }
    }

    private String params(int id) {
        return "[ID:" + id + "]";
    }

    // ---------------- Election Service Impl ----------------

    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    @Override
    public int getNodeId() throws RemoteException {
        return nodeId;
    }

    @Override
    public void startElection(int senderId) throws RemoteException {
        // If I have a higher ID than sender, I take over the election
        if (this.nodeId > senderId) {
            System.out.println("Received Election msg from " + senderId + ". I have higher ID. Taking over.");
            startElectionRoutine();
        }
    }

    @Override
    public void declareCoordinator(int leaderId) throws RemoteException {
        this.currentLeaderId = leaderId;
        this.isCoordinator = (leaderId == this.nodeId);
        System.out.println("NEW COORDINATOR DECLARED: Node " + leaderId);

        if (!isCoordinator) {
            System.out.println("Switching to BACKUP mode. Monitoring Leader...");
            try {
                registry.unbind("QuizService");
                System.out.println(">> Unbound QuizService (Backup Mode).");
            } catch (Exception e) {
                // Ignore if not bound
            }
        }
    }

    // ---------------- Internal Logic ----------------

    private void startElectionRoutine() {
        System.out.println("Starting Election Process...");
        isCoordinator = true; // Assume I win until proven otherwise

        for (int id : allNodes) {
            if (id > this.nodeId) {
                try {
                    String targetHost = config.getProperty("node." + id).split(":")[0];
                    int targetPort = Integer.parseInt(config.getProperty("node." + id).split(":")[1]);
                    Registry targetReg = LocateRegistry.getRegistry(targetHost, targetPort);
                    ElectionService node = (ElectionService) targetReg.lookup("Node_" + id);
                    if (node != null) {
                        node.startElection(this.nodeId);
                        isCoordinator = false; // Someone higher is alive
                    }
                } catch (Exception e) {
                    // Node likely down, continue assuming I might win
                }
            }
        }

        if (isCoordinator) {
            becomeLeader();
        }
    }

    private void becomeLeader() {
        try {
            System.out.println("!!! I AM THE NEW LEADER (Coordinator) !!!");
            currentLeaderId = nodeId;
            isCoordinator = true;

            // Announce to lower nodes
            for (int id : allNodes) {
                if (id < this.nodeId) {
                    try {
                        String targetHost = config.getProperty("node." + id).split(":")[0];
                        int targetPort = Integer.parseInt(config.getProperty("node." + id).split(":")[1]);
                        Registry targetReg = LocateRegistry.getRegistry(targetHost, targetPort);
                        ElectionService node = (ElectionService) targetReg.lookup("Node_" + id);
                        node.declareCoordinator(this.nodeId);
                    } catch (Exception e) {
                        /* Ignore */ }
                }
            }

            // Bind the main QuizService for clients
            registry.rebind("QuizService", quizService);
            System.out.println(">> QuizService bound to Registry on port " + myPort + ". Ready for Clients.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startHeartbeat() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (isCoordinator)
                    return;

                if (currentLeaderId != -1) {
                    try {
                        String leaderHost = config.getProperty("node." + currentLeaderId).split(":")[0];
                        int leaderPort = Integer.parseInt(config.getProperty("node." + currentLeaderId).split(":")[1]);
                        Registry leaderReg = LocateRegistry.getRegistry(leaderHost, leaderPort);
                        ElectionService leader = (ElectionService) leaderReg.lookup("Node_" + currentLeaderId);
                        leader.isAlive(); // Ping
                        // System.out.print("."); // Heartbeat log
                    } catch (Exception e) {
                        System.err.println("\nLeader (Node " + currentLeaderId + ") has CRASHED!");
                        currentLeaderId = -1;
                        startElectionRoutine();
                    }
                } else {
                    // No leader known, try election
                    startElectionRoutine();
                }
            }
        }, 1000, 2000); // Check every 2 seconds
    }

    public void start() {
        try {
            // Get or Create Registry (Specific Port)
            try {
                // Fix for external connectivity: Tell RMI to use the actual IP from config
                // READ CONFIG FOR MY IP
                String myKey = "node." + nodeId;
                String myConfigVal = config.getProperty(myKey);
                String publicIp = "127.0.0.1";

                if (myConfigVal != null) {
                    publicIp = myConfigVal.split(":")[0];
                } else {
                    publicIp = java.net.InetAddress.getLocalHost().getHostAddress();
                }

                System.setProperty("java.rmi.server.hostname", publicIp);
                System.out.println("DEBUG: RMI Hostname set to: " + publicIp);

                // Try to create registry on MY port
                try {
                    registry = LocateRegistry.createRegistry(myPort);
                    System.out.println("Created RMI Registry on port " + myPort);
                } catch (Exception e) {
                    registry = LocateRegistry.getRegistry(myPort); // Already exists?
                    System.out.println("Connected to local RMI Registry on port " + myPort);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Register Self for Election
            registry.rebind("Node_" + nodeId, this);
            System.out.println("Registered as 'Node_" + nodeId + "'");

            // Start Election now that Registry is ready
            startElectionRoutine();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcastReplication(int studentId, int score) {
        new Thread(() -> {
            System.out.println("Broadcasting replication to " + (allNodes.size() - 1) + " nodes...");
            for (int id : allNodes) {
                if (id == this.nodeId)
                    continue; // Skip self

                try {
                    String targetHost = config.getProperty("node." + id).split(":")[0];
                    int targetPort = Integer.parseInt(config.getProperty("node." + id).split(":")[1]);
                    Registry targetReg = LocateRegistry.getRegistry(targetHost, targetPort);
                    // Replication is part of QuizService, so look that up?
                    // Actually, QuizService is bound to "QuizService".
                    // But if we have multiple servers on same host, we rely on the port.
                    // Let's assume on a different port, "QuizService" is the correct name.
                    common.QuizService qs = (common.QuizService) targetReg.lookup("QuizService");
                    qs.replicateSubmission(studentId, score);
                    System.out.println("-> Replicated to Node " + id);
                } catch (Exception e) {
                    // System.err.println("Failed to replicate to Node " + id);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        int id = 1; // Default ID
        if (args.length > 0) {
            id = Integer.parseInt(args[0]);
        }

        try {
            new QuizServer(id).start();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
