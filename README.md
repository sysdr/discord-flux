# Building Discord: From Socket to Scale (The Real-Time Engineering Masterclass)

A 90-day deep dive into building a massive-scale chat and voice platform. We dissect the engineering that powers Discord, Slack, and Telegram. We move from simple point-to-point sockets to designing the "Gateway" pattern, solving the "Thundering Herd" problem, optimizing massive message storage with ScyllaDB patterns, and handling voice signaling via SFUs. This is not just about features; it's about architecture that survives millions of concurrent users.


#### Each lesson should be Hands on - everyday code, learn by coding
#### Programming Language : Java
#### Intermediate to Expert (Scale-up)
#### 90 Lessons
#### Target udience
Fresh Computer Science & Engineering Grads, Software Engineers/Developers, Software designers, software architects, product managers, UI/UX Designers, Quality Assurance (QA) Engineers, SRE, DevOps Engineers, Data Engineers, Project Managers, Engineering Managers and Technical Writers, IT service industry consultants & engineers


---

### 1. Course Details

**Why This Course?**
Most system design resources focus on stateless request/response models (like a Twitter feed or e-commerce cart). But the modern web is real-time. This course fills the critical gap between "building a chat app with socket.io" (which takes a weekend) and "engineering a high-concurrency real-time platform" (which takes a career). We move beyond simple polling to mastering persistent connections, handling the "Thundering Herd," and managing stateful services at a massive scale.

**What You'll Build**
You will build **"Flux,"** a fully functional Discord clone. We start with a single script sending messages and end with a distributed, sharded, microservices-based platform featuring a custom Gateway, Pub/Sub Fan-out logic, Voice SFU routing signaling, and a specialized Cassandra-like storage engine for infinite chat history.

**Who Should Take This Course?**
This is for engineers tired of "Toy Apps." Whether you are a fresh grad wanting to understand how WebSocket clusters work, a backend engineer looking to transition into distributed systems, or a Product Manager wanting to understand why "read receipts" are technically expensive at scale.

**What Makes This Course Different?**

* **No "Magic" Libraries:** We don't just use socket.io and call it a day. We build the connection state management logic.
* **Failure is the Default:** We simulate network partitions and node crashes in code to see how the system recovers.
* **Hybrid Storage:** We don't just dump everything in SQL. We explore why chat history needs Wide-Column stores (Cassandra/Scylla) while user metadata stays in Relational DBs (Postgres).

**Key Topics Covered:**
Persistent WebSockets, Stateful Load Balancing, Consistent Hashing Rings, Pub/Sub Fan-out, Wide-Column Database Modeling (LSM Trees), Voice/Video Signaling (WebRTC & SFU), Rate Limiting Distributed Traffic (Redis Lua), and Ring Buffers for message replay.

**Prerequisites:**
Basic familiarity with a programming language (Python/Go/Node), basic understanding of HTTP/REST, and a willingness to debug async code.

---

### 2. Course Structure & Detailed Curriculum

#### Phase 1: The Connection (Days 1-15)

**Focus:** The fundamental shift from HTTP to Stateful WebSockets. Building the 1:1 prototype.
**Learning Objectives:**

* Understand the OSI model trade-offs between HTTP (Stateless) and WebSockets (Stateful).
* Implement a custom application-level protocol (Opcodes) over raw sockets.
* Master concurrency patterns (Goroutines/Event Loops) to handle 10k+ concurrent connections on a single node.

**Curriculum:**

* **Day 1:** **The Handshake:** Coding a raw HTTP-to-WebSocket upgrade handler from scratch.
* **Day 2:** **The Frame:** Parsing WebSocket binary frames (Fin bit, Opcodes, Masking) manually.
* **Day 3:** **The Loop:** Building the main event loop for a single client connection.
* **Day 4:** **Protocol Design:** Defining JSON payloads and Opcodes (Hello, Identify, Dispatch).
* **Day 5:** **Keep-Alive:** Implementing Heartbeat (Opcode 1) and Heartbeat ACK (Opcode 11) logic.
* **Day 6:** **Zombie Killing:** Coding a background reaper process to drop connections that miss heartbeats.
* **Day 7:** **Concurrency I:** Thread-per-connection vs. Non-blocking I/O (Theory & Benchmark).
* **Day 8:** **Concurrency II:** Implementing a Worker Pool pattern to decouple I/O from processing.
* **Day 9:** **Netpoll Optimization:** (Advanced) Using `epoll`/`kqueue` concepts for massive connection handling.
* **Day 10:** **Session State:** Storing user session data in-memory (Maps vs. Sync.Map).
* **Day 11:** **Serialization:** Benchmarking JSON vs. ETF (Erlang Term Format) or Protobuf for payloads.
* **Day 12:** **The Resume Capability:** Implementing Opcode 6 (Resume) to recover lost connections without re-login.
* **Day 13:** **The Replay Buffer:** Coding a circular buffer to store "missed" messages for disconnected clients.
* **Day 14:** **Load Testing I:** Writing a script to spawn 10,000 dummy clients to test memory usage.
* **Day 15:** **Memory Profiling:** Analyze heap dumps to find memory leaks in the connection handler.

#### Phase 2: The Fan-Out (Days 16-30)

**Focus:** Scaling from two users to a Server (Guild) of thousands. Pub/Sub patterns.
**Learning Objectives:**

* Decouple ingress (API) from egress (Gateway) using a Message Bus.
* Implement "Guild-Centric" routing to minimize broker load.
* Handle "Slow Consumers" using Ring Buffers to prevent server crashes.

**Curriculum:**

* **Day 16:** **Pub/Sub Primitives:** Setting up Redis Pub/Sub vs. Redis Streams.
* **Day 17:** **The Broker:** Connecting the WebSocket Gateway to Redis Streams.
* **Day 18:** **Topic Design:** Why `topic=guild_id` beats `topic=user_id` for chat groups.
* **Day 19:** **The Publisher:** Coding the REST API endpoint that writes messages to Redis.
* **Day 20:** **The Subscriber:** Gateway logic to subscribe to active Guilds on the local node.
* **Day 21:** **Local Fan-Out:** Iterating through local socket maps to broadcast a Redis message to connected users.
* **Day 22:** **Filter Logic:** Implementing "Intents" to stop sending data users didn't ask for.
* **Day 23:** **Backpressure:** Detecting when the OS TCP send buffer is full.
* **Day 24:** **Ring Buffers:** Implementing a user-level output buffer to queue messages for slow clients.
* **Day 25:** **The Drop:** Logic to forcefully disconnect clients who lag too far behind (protecting the server).
* **Day 26:** **Presence System I:** Storing "Online/Offline" state in Redis.
* **Day 27:** **Presence System II:** Broadcasting state changes (The "n-squared" problem).
* **Day 28:** **Lazy Loading:** Coding "Guild Member Chunks" (Opcode 8) to load members on demand.
* **Day 29:** **Typing Indicators:** Implementing ephemeral events that don't need persistence.
* **Day 30:** **Integration Test:** Simulating a 1,000-user Guild chat storm.

#### Phase 3: The Persistence (Days 31-45)

**Focus:** Storing billions of messages. The shift from SQL to LSM Trees (ScyllaDB/Cassandra).
**Learning Objectives:**

* Understand the limitations of B-Trees (SQL) for write-heavy chat logs.
* Model time-series data in Wide-Column stores using Partition Keys and Clustering Keys.
* Implement a distributed, time-sortable ID generator (Snowflake).

**Curriculum:**

* **Day 31:** **The Write Problem:** Benchmarking Postgres INSERTs vs. the need for LSM Trees.
* **Day 32:** **Wide-Column Basics:** Setting up a local ScyllaDB/Cassandra node.
* **Day 33:** **Snowflake IDs I:** Understanding the 64-bit structure (Timestamp + WorkerID + Sequence).
* **Day 34:** **Snowflake IDs II:** Coding a lock-free Snowflake generator in your chosen language.
* **Day 35:** **Schema Design:** Creating the `messages` table with `(channel_id, bucket)` as the partition key.
* **Day 36:** **The Hot Partition:** Why storing all channel messages in one row is bad.
* **Day 37:** **Time Bucketing:** Implementing logic to bucket messages by 10-day windows in the Partition Key.
* **Day 38:** **Consistency Levels:** Coding reads/writes with `LOCAL_QUORUM` vs. `ONE`.
* **Day 39:** **Pagination:** Implementing "Load More" using `message_id` seeking (Cluster Key scanning).
* **Day 40:** **Message Deletion:** Understanding "Tombstones" and why we don't `DELETE` immediately.
* **Day 41:** **Compaction Strategies:** Configuring Time-Window Compaction Strategy (TWCS) for chat logs.
* **Day 42:** **Data Services:** Building a gRPC layer between the API and ScyllaDB.
* **Day 43:** **Request Coalescing:** Coding a "Singleflight" mechanism to merge duplicate read requests.
* **Day 44:** **Read States:** Implementing the "Ack" tracking system (Unread markers).
* **Day 45:** **Migration Simulation:** Writing a script to migrate data from a JSON file to Scylla without downtime.

#### Phase 4: The Gateway (Days 46-60)

**Focus:** Managing millions of concurrent connections. Load balancing stateful services.
**Learning Objectives:**

* Solve the "Session Locality" problem using Consistent Hashing.
* Implement Sharding to distribute heavy load (e.g., Music Bots) across connections.
* Build a Service Discovery mechanism for the Gateway cluster.

**Curriculum:**

* **Day 46:** **The Cluster:** Containerizing the Gateway service (Docker Compose).
* **Day 47:** **Service Discovery:** Setting up Etcd/Redis to track active Gateway nodes.
* **Day 48:** **Consistent Hashing I:** Theory of the Ring and Key placement.
* **Day 49:** **Consistent Hashing II:** implementing the Ring data structure with binary search lookup.
* **Day 50:** **Virtual Nodes:** Adding Vnodes to the Ring to fix data skew.
* **Day 51:** **Node Rebalancing:** Handling the "Add Node" event and minimizing key movement.
* **Day 52:** **Sharding Logic I:** Calculating `shard_id = (guild_id >> 22) % total_shards`.
* **Day 53:** **Sharding Logic II:** Enforcing shard identity in the Identify (Opcode 2) payload.
* **Day 54:** **Inter-Node Communication:** Forwarding events between Gateways using the Message Bus.
* **Day 55:** **The Edge Proxy:** Setting up Nginx/Envoy to route WebSocket upgrades.
* **Day 56:** **Global Rate Limiting:** Implementing a distributed rate limiter for Gateway logins (Identify).
* **Day 57:** **Client-Side Cache:** Building a local SQLite store for the client to work offline.
* **Day 58:** **Sync Engine:** Logic to sync local client DB with the server on reconnection.
* **Day 59:** **Deployment:** Rolling updates for stateful WebSockets (Drain vs. Kill).
* **Day 60:** **Gateway Observability:** Metrics for "Connected Clients" and "Events Per Second" (Prometheus).

#### Phase 5: The Signaling (Days 61-75)

**Focus:** Voice & Video architecture. WebRTC, ICE servers, and SFU basics.
**Learning Objectives:**

* Understand the difference between P2P Mesh, MCU, and SFU architectures.
* Implement WebRTC Signaling over the existing WebSocket connection.
* Build a Selective Forwarding Unit (SFU) to route RTP packets.

**Curriculum:**

* **Day 61:** **UDP vs TCP:** Coding a simple UDP packet echo server.
* **Day 62:** **WebRTC Basics:** Understanding SDP (Session Description Protocol) and ICE Candidates.
* **Day 63:** **Signaling Overlay:** Adding "Voice State Update" (Opcode 4) to the Gateway.
* **Day 64:** **The Offer/Answer Dance:** Exchanging JSON-RPC SDP payloads over WebSockets.
* **Day 65:** **NAT Traversal:** Setting up a STUN server (coturn) and testing connectivity.
* **Day 66:** **SFU Architecture:** Why we need a Selective Forwarding Unit (SFU) for groups.
* **Day 67:** **Building an SFU I:** Using a library (Pion/Mediasoup) to accept an RTP stream.
* **Day 68:** **Building an SFU II:** Forwarding the RTP track to other subscribers (Fan-out).
* **Day 69:** **Audio Mixing:** Why we *don't* mix audio on the server (Client-side mixing).
* **Day 70:** **Speaking Indicators:** Parsing RTP Header Extensions (RFC 6464) to detect audio levels.
* **Day 71:** **Opcode 5 (Speaking):** Broadcasting "User is speaking" events based on RTP headers.
* **Day 72:** **Video Simulcast:** Understanding High/Med/Low quality stream switching.
* **Day 73:** **DTLS/SRTP:** Basics of encryption for Voice packets (Security).
* **Day 74:** **Region Selection:** Logic to pick the closest Voice Server to the Guild region.
* **Day 75:** **Voice Quality Metrics:** Tracking Packet Loss and Jitter (RTCP reports).

#### Phase 6: The Fortification (Days 76-90)

**Focus:** Reliability, Rate Limiting, Sharding, and Production deployment.
**Learning Objectives:**

* Master "Thundering Herd" mitigation strategies using Jitter and Backoff.
* Implement Distributed Rate Limiting using Redis Lua scripts.
* Scale Search infrastructure using ElasticSearch optimization.

**Curriculum:**

* **Day 76:** **The Thundering Herd:** Simulating a 100k user disconnect and immediate reconnect.
* **Day 77:** **Jitter & Backoff:** Implementing randomized delay in the client reconnect logic.
* **Day 78:** **Distributed Rate Limiting I:** The Token Bucket algorithm theory.
* **Day 79:** **Distributed Rate Limiting II:** Writing atomic Lua scripts for Redis rate limiting.
* **Day 80:** **Push Notifications:** Architecture for queuing mobile pushes (APNS/FCM) asynchronously.
* **Day 81:** **Search Architecture:** Setting up ElasticSearch for message indexing.
* **Day 82:** **Bulk Indexing:** Optimizing search ingestion (Batching updates).
* **Day 83:** **Image Processing:** Building an image proxy to resize uploads on the fly (Go/C++).
* **Day 84:** **CDN Strategy:** Uploading assets to S3/Cloudflare R2 and generating signed URLs.
* **Day 85:** **Bot Ecosystem:** Designing the API for external Bots (Webhooks and Interactions).
* **Day 86:** **Chaos Engineering:** Writing a script to randomly kill Gateway nodes and verify recovery.
* **Day 87:** **Caching Strategy:** Implementing "Read-Through" caching for User Profiles.
* **Day 88:** **Permissions System:** Bitwise logic for Role hierarchies and Permission overwrites.
* **Day 89:** **Code Freeze:** Final end-to-end testing of Flux (Chat + Voice + Persistence).
* **Day 90:** **Post-Mortem & Future:** Discussing what breaks next (100M users) and Discord's move to Rust.
