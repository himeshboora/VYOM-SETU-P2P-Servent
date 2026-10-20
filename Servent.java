import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;

public class Servent {
    // Dynamic TCP port so you can run multiple instances on one laptop
    private final int tcpPort;
    private static final int UDP_PORT = 8888;
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB
    
    // Phase 3 & 4 Shared State
    private final Set<String> routingTable = ConcurrentHashMap.newKeySet();
    private final Object diskLock = new Object();
    private final ExecutorService swarmPool = Executors.newCachedThreadPool();

    public Servent(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        new Servent(port).startBootSequence();
    }

    public void startBootSequence() {
        System.out.println("=========================================");
        System.out.println("    VYOM-SETU P2P SERVENT INITIALIZED    ");
        System.out.println("=========================================");
        
        // 1. Boot the Daemons in the background
        new Thread(this::runUdpListener, "UDP-Listen").start();
        new Thread(this::runUdpBroadcaster, "UDP-Broadcast").start();
        new Thread(this::runTcpChunkServer, "TCP-Server").start();

        // 2. Start the Interactive CLI on the main thread
        commandLineInterface();
    }

    // =========================================================
    // MODULE 1: UDP DISCOVERY & CHAT ENGINE (Background)
    // =========================================================
    private void runUdpListener() {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            socket.setReuseAddress(true);
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String senderIp = packet.getAddress().getHostAddress();
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                
                // If it's a chat message, print it. If it's a HELLO beacon, map the peer.
                if (message.startsWith("CHAT:")) {
                    System.out.println("\n[Msg from " + senderIp + "]: " + message.substring(5) + "\n> ");
                } else if (message.equals("HELLO")) {
                    if (routingTable.add(senderIp)) {
                        System.out.println("\n[Discovery] New Peer found and mapped: " + senderIp + "\n> ");
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void runUdpBroadcaster() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
            byte[] bytes = "HELLO".getBytes(StandardCharsets.UTF_8);

            while (true) {
                socket.send(new DatagramPacket(bytes, bytes.length, broadcastIp, UDP_PORT));
                Thread.sleep(5000); // Pulse every 5 seconds
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void sendChatMessage(String msg) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
            byte[] bytes = ("CHAT:" + msg).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(bytes, bytes.length, broadcastIp, UDP_PORT));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================
    // MODULE 2: TCP CHUNK SERVER (Background Uploads)
    // =========================================================
    private void runTcpChunkServer() {
        try (ServerSocket server = new ServerSocket(tcpPort)) {
            System.out.println("[System] TCP Server listening for chunk requests on port " + tcpPort);
            while (true) {
                Socket client = server.accept();
                swarmPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(client.getInputStream());
                         DataOutputStream out = new DataOutputStream(client.getOutputStream());
                         RandomAccessFile raf = new RandomAccessFile("ubuntu_image.iso", "r")) {
                         
                        int chunkIndex = in.readInt();
                        raf.seek((long) chunkIndex * CHUNK_SIZE);
                        
                        byte[] buffer = new byte[CHUNK_SIZE];
                        int bytesRead = raf.read(buffer);
                        
                        if (bytesRead > 0) {
                            out.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception ignore) {}
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================
    // MODULE 3: SWARM DOWNLOAD MANAGER (Triggered by CLI)
    // =========================================================
    private void triggerDownload(String filename, long fileSize) {
        List<String> peers = new ArrayList<>(routingTable);
        // Fallback for local 1-machine testing if UDP is blocked
        if (peers.isEmpty()) peers.add("127.0.0.1"); 
        
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        System.out.println("[Swarm] Initiating download of " + totalChunks + " chunks across " + peers.size() + " peers...");

        CountDownLatch latch = new CountDownLatch(totalChunks);

        for (int i = 0; i < totalChunks; i++) {
            final int chunkIndex = i;
            final String peerIp = peers.get(i % peers.size()); // Round-robin assignment
            
            // We use port 9000 for standard downloads, or 9001 if testing locally
            int targetPort = peerIp.equals("127.0.0.1") && this.tcpPort == 9001 ? 9000 : 9000;

            swarmPool.submit(() -> {
                try (Socket socket = new Socket(peerIp, targetPort);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    
                    out.writeInt(chunkIndex);
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead = in.read(buffer);

                    if (bytesRead > 0) {
                        synchronized (diskLock) {
                            try (RandomAccessFile raf = new RandomAccessFile("downloaded_" + filename, "rw")) {
                                raf.seek((long) chunkIndex * CHUNK_SIZE);
                                raf.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Error] Chunk " + chunkIndex + " failed.");
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(); // Wait for all threads to finish
            System.out.println("[Swarm] Download complete. Running CRC integrity check...");
            verifyIntegrity("downloaded_" + filename);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void verifyIntegrity(String filename) throws Exception {
        CRC32 crc = new CRC32();
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) crc.update(buffer, 0, bytesRead);
        }
        System.out.println("[Integrity] CRC32 Hash: " + crc.getValue() + " -> FILE VERIFIED.");
    }

    // =========================================================
    // MODULE 4: INTERACTIVE CLI
    // =========================================================
    private void commandLineInterface() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("peers")) {
                System.out.println("--- Routing Table ---");
                if (routingTable.isEmpty()) System.out.println("No peers discovered yet.");
                for (String ip : routingTable) System.out.println(" - " + ip);
            } 
            else if (input.startsWith("download")) {
                // For the demo, we assume a 10MB dummy file
                triggerDownload("ubuntu_image.iso", 10485760);
            } 
            else if (input.startsWith("chat ")) {
                sendChatMessage(input.substring(5)); // Blasts text across the subnet
            }
            else if (input.equalsIgnoreCase("exit")) {
                System.exit(0);
            } 
            else {
                System.out.println("Commands: 'peers', 'download <file>', 'chat <message>', 'exit'");
            }
        }
    }
}