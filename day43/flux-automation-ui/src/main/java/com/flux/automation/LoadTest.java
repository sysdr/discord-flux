package com.flux.automation;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

public class LoadTest {
    
    public static void main(String[] args) throws Exception {
        String baseUrl = "http://localhost:8080";
        int concurrentWorkflows = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        
        System.out.println("🔥 Starting Load Test: " + concurrentWorkflows + " concurrent workflows");
        
        HttpClient client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
        
        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/workflows"))
            .GET()
            .build();
        
        HttpResponse<String> listResponse = client.send(listRequest, 
            HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Available workflows: " + listResponse.body());
        
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        long startTime = System.nanoTime();
        
        for (int i = 0; i < concurrentWorkflows; i++) {
            String workflowId = i % 3 == 0 ? "linear-workflow" :
                               i % 3 == 1 ? "parallel-workflow" :
                               "complex-workflow";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/execute?workflowId=" + workflowId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            
            CompletableFuture<HttpResponse<String>> future = 
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long submitDuration = (System.nanoTime() - startTime) / 1_000_000;
        
        System.out.println("\n✓ All workflows submitted in " + submitDuration + "ms");
        System.out.println("⏱️  Waiting for executions to complete...");
        
        Thread.sleep(15000);
        
        System.out.println("\n📊 Load Test Complete!");
        System.out.println("   Total Workflows: " + concurrentWorkflows);
        System.out.println("   Submission Time: " + submitDuration + "ms");
        
        System.exit(0);
    }
}
