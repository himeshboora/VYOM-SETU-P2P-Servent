# VYOM-SETU-P2P-Servent
Vyom-Setu is a decentralized, serverless P2P file-sharing app built in Java for local networks. It features UDP subnet broadcasting for instant peer discovery and chat, alongside multithreaded TCP chunk swarming and CRC32 hashing for secure, concurrent file transfers.
VYOM-SETU P2P Servent

A serverless, decentralized Peer-to-Peer (P2P) file sharing and chat application built in pure Java. This project was developed as a final presentation for a Computer Networks and Data Communications course, demonstrating practical implementations of the OSI Network and Transport layers.

🚀 Features

Decentralized Architecture: Operates as a "Servent" (Server + Client). There is no central server; every node acts as both a peer seeker and a chunk provider.

UDP Subnet Discovery: Uses UDP Datagram broadcasting to automatically discover and map active peers on the local subnet without hardcoded IP addresses.

Real-time Peer Chat: Lightweight messaging system utilizing the UDP discovery channel for cross-node communication.

TCP Multithreaded Swarm Downloading: Splits files into 512KB chunks. Uses a Java ExecutorService thread pool to request and download chunks concurrently over TCP streams.

Thread-Safe Disk I/O: Utilizes Java's RandomAccessFile with OS-level mutex locking (synchronized(diskLock)) to ensure safe concurrent file writing.

CRC32 Integrity Verification: Automatically calculates and verifies the checksum of the fully assembled file against the source to guarantee zero packet loss or corruption.

🛠️ System Architecture

Language: Java (JDK 25)

Network Layer (Discovery/Chat): UDP Broadcast on Port 8888

Transport Layer (File Transfer): TCP ServerSocket on Port 9000

Concurrency: java.util.concurrent.ExecutorService

I/O Handling: java.io.RandomAccessFile, java.nio

📋 Prerequisites & Network Setup

To run this application across multiple computers, strict local network conditions must be met:

Shared Subnet: All machines must be on the exact same local network.

Recommended: Use a Windows Mobile Hotspot hosted by one of the laptops (e.g., 192.168.137.x subnet). Third-party phone hotspots often use "Client Isolation" which blocks P2P traffic.

Disable Virtual Adapters: Disable extra network adapters (VPNs, VirtualBox/VMware virtual switches) to ensure Java binds to the correct Wi-Fi interface.

Firewall Configuration:

Windows Defender Firewall must allow Java(TM) Platform SE binary for both Private and Public networks to accept inbound TCP/UDP packets.

💻 How to Run

Open your terminal or command prompt and navigate to the project directory.

Compile the Java file:

javac Servent.java


Run the Servent application, specifying the TCP listening port (e.g., 9000):

java Servent 9000


⌨️ Command Line Interface (CLI)

Once the application initializes, it will automatically begin listening for UDP broadcasts and TCP chunk requests. You can interact with the network using the following commands:

chat <your message>

Example: chat Hello network!

Broadcasts a message to all discovered peers on the subnet.

download <filename.ext>

Example: download ubuntu_image.iso

Initiates a multithreaded swarm download from available peers. The requested file must exist in the root directory of the seeding peer.

peers (If implemented in your CLI loop)

Lists the IP addresses of all currently discovered nodes on the local network.

⚠️ Troubleshooting

I don't see my partner's chat messages: Ensure your partner is broadcasting to the correct subnet mask (e.g., 192.168.137.255 instead of 255.255.255.255 if routing issues occur) and that the receiving Windows Firewall has the "Public" profile checked for Java.

[Error] Chunk X failed: The seeding node's TCP server must be actively running (java Servent 9000), and the requested file must be present in their working directory.

java.net.BindException: Address already in use: The port (9000 or 8888) is being held by a previous instance of the program. Close the terminal completely or forcefully terminate the Java process and restart.

NoSuchElementException: No line found: This is a harmless CLI exit code that occurs when terminating the program (Ctrl+C) while the Scanner is awaiting input.
