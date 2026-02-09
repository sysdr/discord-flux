package com.flux.automation;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP server exposing workflow execution API and real-time dashboard.
 */
public class AutomationServer {
    
    private final HttpServer server;
    private final ExecutionEngine engine;
    private final StateStore stateStore;
    private final Map<String, WorkflowDefinition> workflows;
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong completedExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    
    public AutomationServer(int port) throws IOException {
        this.engine = new ExecutionEngine();
        this.stateStore = new StateStore(Paths.get("./workflows"));
        this.workflows = new ConcurrentHashMap<>();
        
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        server.createContext("/favicon.ico", this::handleFavicon);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/workflows", this::handleWorkflows);
        server.createContext("/api/workflows/", this::handleWorkflowById);
        server.createContext("/api/execute", this::handleExecute);
        server.createContext("/api/executions", this::handleExecutions);
        server.createContext("/api/metrics", this::handleMetrics);
        server.createContext("/dashboard", this::handleDashboard);
        
        seedSampleWorkflows();
    }
    
    public void start() {
        server.start();
        System.out.println("🚀 Automation Server started on port " + server.getAddress().getPort());
        System.out.println("📊 Dashboard: http://localhost:" + server.getAddress().getPort() + "/dashboard");
    }
    
    public void stop() {
        server.stop(0);
        engine.shutdown();
        stateStore.close();
    }
    
    private void handleFavicon(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }
    
    private void handleRoot(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || path.isEmpty()) {
            exchange.getResponseHeaders().add("Location", "/dashboard");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        sendJsonResponse(exchange, 404, "{\"error\":\"Not found\"}");
    }
    
    private void handleWorkflows(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            StringBuilder json = new StringBuilder("[");
            int i = 0;
            for (WorkflowDefinition workflow : workflows.values()) {
                if (i++ > 0) json.append(",");
                json.append(workflowToJson(workflow));
            }
            json.append("]");
            sendJsonResponse(exchange, 200, json.toString());
        } else if ("POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 201, "{\"message\":\"Workflow created\"}");
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }
    
    private void handleWorkflowById(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String workflowId = path.substring(path.lastIndexOf('/') + 1);
        
        WorkflowDefinition workflow = workflows.get(workflowId);
        if (workflow == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Workflow not found\"}");
            return;
        }
        
        sendJsonResponse(exchange, 200, workflowToJson(workflow));
    }
    
    private void handleExecute(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String query = exchange.getRequestURI().getQuery();
        String workflowId = extractParam(query, "workflowId");
        
        if (workflowId == null) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing workflowId\"}");
            return;
        }
        
        WorkflowDefinition workflow = workflows.get(workflowId);
        if (workflow == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Workflow not found\"}");
            return;
        }
        
        totalExecutions.incrementAndGet();
        
        var result = engine.executeAsync(workflow);
        WorkflowExecution execution = result.execution();
        CompletableFuture<WorkflowExecution> future = result.future();
        
        if (execution != null) {
            stateStore.recordWorkflowStart(execution);
            
            future.thenAccept(completed -> {
                if (completed.getStatus() == ExecutionStatus.COMPLETED) {
                    completedExecutions.incrementAndGet();
                } else {
                    failedExecutions.incrementAndGet();
                }
                stateStore.recordWorkflowComplete(completed);
                for (StepResult stepResult : completed.getStepResults().values()) {
                    stateStore.recordStepResult(completed.getExecutionId(), stepResult);
                }
            });
            
            String json = String.format(
                "{\"executionId\":\"%s\",\"status\":\"%s\"}",
                execution.getExecutionId(), execution.getStatus()
            );
            sendJsonResponse(exchange, 200, json);
        } else {
            totalExecutions.decrementAndGet();
            sendJsonResponse(exchange, 500, "{\"error\":\"Failed to start execution\"}");
        }
    }
    
    private void handleExecutions(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        Collection<WorkflowExecution> executions = engine.getActiveExecutions();
        
        StringBuilder json = new StringBuilder("[");
        int i = 0;
        for (WorkflowExecution exec : executions) {
            if (i++ > 0) json.append(",");
            json.append(executionToJson(exec));
        }
        json.append("]");
        
        sendJsonResponse(exchange, 200, json.toString());
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String json = String.format(
            "{\"totalExecutions\":%d,\"completedExecutions\":%d,\"failedExecutions\":%d,\"activeExecutions\":%d,\"workflowCount\":%d}",
            totalExecutions.get(),
            completedExecutions.get(),
            failedExecutions.get(),
            engine.getActiveExecutions().size(),
            workflows.size()
        );
        sendJsonResponse(exchange, 200, json);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = getDashboardHtml();
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        sendResponse(exchange, 200, html);
    }
    
    private String workflowToJson(WorkflowDefinition workflow) {
        return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"stepCount\":%d}",
            workflow.id(), workflow.name(), workflow.description(), workflow.steps().size());
    }
    
    private String executionToJson(WorkflowExecution execution) {
        return String.format(
            "{\"executionId\":\"%s\",\"workflowId\":\"%s\",\"workflowName\":\"%s\",\"status\":\"%s\",\"progress\":%.2f,\"durationMs\":%d}",
            execution.getExecutionId(), execution.getWorkflow().id(),
            execution.getWorkflow().name(), execution.getStatus(),
            execution.getProgress() * 100, execution.durationMs()
        );
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        sendResponse(exchange, statusCode, json);
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private String extractParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equals(param)) {
                return kv[1];
            }
        }
        return null;
    }
    
    private void seedSampleWorkflows() {
        WorkflowDefinition linear = new WorkflowDefinition(
            "linear-workflow",
            "Linear Processing",
            "Three sequential steps",
            List.of(
                StepDefinition.builder("step1", "Fetch Data", StepType.HTTP)
                    .config("durationMs", "2000")
                    .build(),
                StepDefinition.builder("step2", "Process Data", StepType.SCRIPT)
                    .dependsOn("step1")
                    .config("durationMs", "2000")
                    .build(),
                StepDefinition.builder("step3", "Save Results", StepType.DATABASE)
                    .dependsOn("step2")
                    .config("durationMs", "2000")
                    .build()
            )
        );
        workflows.put(linear.id(), linear);
        
        WorkflowDefinition parallel = new WorkflowDefinition(
            "parallel-workflow",
            "Parallel Processing",
            "Fan-out and fan-in pattern",
            List.of(
                StepDefinition.builder("init", "Initialize", StepType.SCRIPT)
                    .config("durationMs", "1000")
                    .build(),
                StepDefinition.builder("task-a", "Task A", StepType.HTTP)
                    .dependsOn("init")
                    .config("durationMs", "3000")
                    .build(),
                StepDefinition.builder("task-b", "Task B", StepType.HTTP)
                    .dependsOn("init")
                    .config("durationMs", "3000")
                    .build(),
                StepDefinition.builder("task-c", "Task C", StepType.HTTP)
                    .dependsOn("init")
                    .config("durationMs", "3000")
                    .build(),
                StepDefinition.builder("finalize", "Finalize", StepType.DATABASE)
                    .dependsOn("task-a", "task-b", "task-c")
                    .config("durationMs", "2000")
                    .build()
            )
        );
        workflows.put(parallel.id(), parallel);
        
        WorkflowDefinition complex = new WorkflowDefinition(
            "complex-workflow",
            "Complex DAG",
            "Multi-level dependencies",
            List.of(
                StepDefinition.builder("fetch-users", "Fetch Users", StepType.HTTP)
                    .config("durationMs", "1000")
                    .build(),
                StepDefinition.builder("fetch-orders", "Fetch Orders", StepType.HTTP)
                    .config("durationMs", "1500")
                    .build(),
                StepDefinition.builder("join-data", "Join Data", StepType.SCRIPT)
                    .dependsOn("fetch-users", "fetch-orders")
                    .config("durationMs", "2000")
                    .build(),
                StepDefinition.builder("send-email", "Send Email", StepType.EMAIL)
                    .dependsOn("join-data")
                    .config("durationMs", "500")
                    .build(),
                StepDefinition.builder("update-crm", "Update CRM", StepType.DATABASE)
                    .dependsOn("join-data")
                    .config("durationMs", "1000")
                    .build(),
                StepDefinition.builder("log-analytics", "Log Analytics", StepType.DATABASE)
                    .dependsOn("send-email", "update-crm")
                    .config("durationMs", "500")
                    .build()
            )
        );
        workflows.put(complex.id(), complex);
    }
    
    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Automation Dashboard</title>
    <meta charset="UTF-8">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #333; min-height: 100vh; padding: 20px; }
        .container { max-width: 1400px; margin: 0 auto; }
        header { background: white; padding: 30px; border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1); margin-bottom: 30px; }
        h1 { font-size: 32px; color: #667eea; margin-bottom: 8px; }
        .subtitle { color: #666; font-size: 14px; }
        .metrics-row { display: flex; gap: 20px; margin-bottom: 30px; flex-wrap: wrap; }
        .metric-card { background: white; padding: 20px; border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1); flex: 1; min-width: 140px; }
        .metric-value { font-size: 28px; font-weight: 700; color: #667eea; }
        .metric-label { font-size: 12px; color: #666; margin-top: 4px; }
        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 30px; }
        .panel { background: white; border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1); padding: 25px; }
        .panel-header { margin-bottom: 20px; padding-bottom: 15px; border-bottom: 2px solid #f0f0f0; }
        .panel-title { font-size: 20px; font-weight: 600; color: #333; }
        .workflow-card { background: #f8f9fa; border: 2px solid #e9ecef; border-radius: 8px;
            padding: 15px; margin-bottom: 12px; cursor: pointer; transition: all 0.2s; }
        .workflow-card:hover { border-color: #667eea; transform: translateX(4px);
            box-shadow: 0 2px 8px rgba(102, 126, 234, 0.2); }
        .workflow-name { font-weight: 600; font-size: 16px; color: #333; margin-bottom: 4px; }
        .workflow-desc { font-size: 13px; color: #666; }
        .workflow-steps { font-size: 12px; color: #999; margin-top: 8px; }
        .execution-item { background: #f8f9fa; border-left: 4px solid #667eea;
            border-radius: 6px; padding: 15px; margin-bottom: 12px; }
        .execution-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
        .execution-name { font-weight: 600; font-size: 15px; color: #333; }
        .status-badge { padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
        .status-running { background: #fef3c7; color: #92400e; }
        .status-completed { background: #d1fae5; color: #065f46; }
        .status-failed { background: #fee2e2; color: #991b1b; }
        .progress-bar { width: 100%; height: 8px; background: #e9ecef; border-radius: 4px; overflow: hidden; margin-top: 10px; }
        .progress-fill { height: 100%; background: linear-gradient(90deg, #667eea 0%, #764ba2 100%); transition: width 0.3s ease; }
        .execution-meta { display: flex; gap: 20px; margin-top: 10px; font-size: 12px; color: #666; }
        .empty-state { text-align: center; padding: 40px; color: #999; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>Automation Dashboard</h1>
            <p class="subtitle">Production-grade workflow orchestration engine</p>
        </header>
        
        <div class="metrics-row">
            <div class="metric-card">
                <div class="metric-value" id="metricTotal">0</div>
                <div class="metric-label">Total Executions</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="metricCompleted">0</div>
                <div class="metric-label">Completed</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="metricFailed">0</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="metricActive">0</div>
                <div class="metric-label">Active Now</div>
            </div>
            <div class="metric-card">
                <div class="metric-value" id="metricWorkflows">0</div>
                <div class="metric-label">Workflows</div>
            </div>
        </div>
        
        <div class="grid">
            <div class="panel">
                <div class="panel-header"><h2 class="panel-title">Available Workflows</h2></div>
                <div id="workflowsList"></div>
            </div>
            <div class="panel">
                <div class="panel-header"><h2 class="panel-title">Active Executions</h2></div>
                <div id="executionsList"></div>
            </div>
        </div>
    </div>
    
    <script>
        let workflows = [];
        let executions = [];
        
        async function loadWorkflows() {
            try {
                const response = await fetch('/api/workflows');
                workflows = await response.json();
                renderWorkflows();
            } catch (error) { console.error('Failed to load workflows:', error); }
        }
        
        async function loadExecutions() {
            try {
                const response = await fetch('/api/executions');
                executions = await response.json();
                renderExecutions();
            } catch (error) { console.error('Failed to load executions:', error); }
        }
        
        async function loadMetrics() {
            try {
                const response = await fetch('/api/metrics');
                const metrics = await response.json();
                document.getElementById('metricTotal').textContent = metrics.totalExecutions;
                document.getElementById('metricCompleted').textContent = metrics.completedExecutions;
                document.getElementById('metricFailed').textContent = metrics.failedExecutions;
                document.getElementById('metricActive').textContent = metrics.activeExecutions;
                document.getElementById('metricWorkflows').textContent = metrics.workflowCount;
            } catch (error) { console.error('Failed to load metrics:', error); }
        }
        
        function renderWorkflows() {
            const container = document.getElementById('workflowsList');
            if (workflows.length === 0) {
                container.innerHTML = '<div class="empty-state"><p>No workflows available</p></div>';
                return;
            }
            container.innerHTML = workflows.map(w => `
                <div class="workflow-card" onclick="executeWorkflow('${w.id}')">
                    <div class="workflow-name">${w.name}</div>
                    <div class="workflow-desc">${w.description}</div>
                    <div class="workflow-steps">${w.stepCount} steps</div>
                </div>
            `).join('');
        }
        
        function renderExecutions() {
            const container = document.getElementById('executionsList');
            if (executions.length === 0) {
                container.innerHTML = '<div class="empty-state"><p>No active executions</p><p style="font-size: 12px; margin-top: 8px;">Click a workflow to start</p></div>';
                return;
            }
            container.innerHTML = executions.map(e => `
                <div class="execution-item">
                    <div class="execution-header">
                        <div class="execution-name">${e.workflowName}</div>
                        <span class="status-badge status-${e.status.toLowerCase()}">${e.status}</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${e.progress}%"></div>
                    </div>
                    <div class="execution-meta">
                        <span>ID: ${e.executionId.substring(0, 8)}</span>
                        <span>Duration: ${Math.round(e.durationMs / 1000)}s</span>
                        <span>Progress: ${Math.round(e.progress)}%</span>
                    </div>
                </div>
            `).join('');
        }
        
        async function executeWorkflow(workflowId) {
            try {
                const response = await fetch(`/api/execute?workflowId=${workflowId}`, { method: 'POST' });
                const result = await response.json();
                await loadExecutions();
                await loadMetrics();
            } catch (error) { console.error('Failed to execute workflow:', error); }
        }
        
        function refresh() {
            loadWorkflows();
            loadExecutions();
            loadMetrics();
        }
        
        setInterval(refresh, 1000);
        refresh();
    </script>
</body>
</html>
""";
    }
    
    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
            AutomationServer server = new AutomationServer(port);
            server.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Shutting down Automation Server...");
                server.stop();
            }));
            
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
