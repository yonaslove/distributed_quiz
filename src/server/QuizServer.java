package server;

import common.ElectionService;
import common.QuizService;
import java.rmi.Naming;
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
    private static final int[] ALL_NODES = { 1, 2 }; // Simple 2-node cluster for demo

    private QuizServiceImpl quizService;
    private Registry registry;

    public QuizServer(int id) throws RemoteException {
        super();
        this.nodeId = id;
        this.quizService = new QuizServiceImpl();

        System.out.println("Server Node " + params(nodeId) + " started.");

        startHeartbeat();
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
        }
    }

    // ---------------- Internal Logic ----------------

    private void startElectionRoutine() {
        System.out.println("Starting Election Process...");
        isCoordinator = true; // Assume I win until proven otherwise

        for (int id : ALL_NODES) {
            if (id > this.nodeId) {
                try {
                    ElectionService node = (ElectionService) registry.lookup("Node_" + id);
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
            for (int id : ALL_NODES) {
                if (id < this.nodeId) {
                    try {
                        ElectionService node = (ElectionService) registry.lookup("Node_" + id);
                        node.declareCoordinator(this.nodeId);
                    } catch (Exception e) {
                        /* Ignore */ }
                }
            }

            // Bind the main QuizService for clients
            registry.rebind("QuizService", quizService);
            System.out.println(">> QuizService bound to Registry. Ready for Clients.");

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
                        ElectionService leader = (ElectionService) registry.lookup("Node_" + currentLeaderId);
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
            // Get or Create Registry (Shared on port 1099 for simplicity)
            try {
                // Fix for external connectivity: Tell RMI to use the actual IP, not localhost
                System.setProperty("java.rmi.server.hostname", java.net.InetAddress.getLocalHost().getHostAddress());

                registry = LocateRegistry.createRegistry(1099);
                System.out.println("Created RMI Registry on port 1099.");
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(1099);
                System.out.println("Connected to existing RMI Registry.");
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
