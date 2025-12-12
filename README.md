# Distributed Online Quiz System

## Overview
This project is a modern implementation of a Distributed Online Quiz System using **Java RMI**, **JDBC (MySQL)**, and **Swing (Modern UI)**.

## Why is this a "Distributed System"?
This project goes beyond a simple Client-Server app by implementing core **Distributed Systems Principles**:

### 1. Remote Method Invocation (RMI)
- **Concept**: Objects running in one JVM (Client) invoke methods on an object in another JVM (Server).
- **Implementation**: The `QuizService` interface is exposed remotely. The client calls `service.getQuestions()` as if it were a local method, but it executes over the network.

### 2. Clock Synchronization (Cristian's Algorithm Logic)
- **Concept**: Distributed machines have different physical clocks. They must synchronize to ensure fairness (e.g., quiz timers).
- **Implementation**: The Client fetches `serverTime`, calculates the `networkDelay` (offset), and adjusts its local UI clock to match the Server's time.

### 3. Mutual Exclusion (Thread Safety)
- **Concept**: Multiple processes/threads accessing a shared resource (File/DB) must not interfere with each other.
- **Implementation**: The Server uses a `synchronized` block in `logResultToFile()` to ensure that if 100 students submit at once, the results are written to the file one by one, preventing data corruption.

### 4. Fault Tolerance (Leader Election)
- **Concept**: The system must survive if the main server crashes.
- **Implementation**: We implemented the **Bully Algorithm**.
  - If Server 1 (Leader) crashes, Server 2 (Backup) detects the heartbeat failure.
  - Server 2 declares itself the **NEW COORDINATOR**.
  - Clients automatically `rebind` to the new server.

### 5. Code Migration (Mobile Code)
- **Concept**: Moving *code* (logic) to the data/client instead of just moving data to the code.
- **Implementation**: The Server sends a `ShuffleStrategy` object (serialized code) to the Client. The Client executes this logic locally to shuffle questions. This offloads processing from the Server to the Client (Load Balancing).

---

## How to Run (Terminal)

### 1. Build the Project
Open a terminal in the project folder and run:
```powershell
.\build.bat
```

### 2. Start the Servers (Cluster)
You need two terminals for the distributed server cluster.

**Terminal 1 (Main Node):**
```powershell
.\run-server.bat
```

**Terminal 2 (Backup Node):**
```powershell
.\run-backup-server.bat
```
*You will see them communicating via Heartbeats.*

### 3. Start the Client
**Terminal 3 (Student):**
```powershell
.\run-client.bat
```

---

## Technical Stack
- **Language**: Java Verified v25
- **Communication**: Java RMI
- **Database**: MySQL (with Mock Fallback)
- **GUI**: Swing (Flat Design)
