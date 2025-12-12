package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ElectionService extends Remote {
    // Ping check
    boolean isAlive() throws RemoteException;

    // Bully Algorithm Messages
    void startElection(int senderId) throws RemoteException;

    void declareCoordinator(int leaderId) throws RemoteException;

    // Get ID of this node
    int getNodeId() throws RemoteException;
}
