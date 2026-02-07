package com.flux.pagination;

import com.datastax.oss.driver.api.core.CqlSession;
import com.flux.model.PageResult;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaginationServiceTest {
    
    private static CassandraClient cassandraClient;
    private static PaginationService paginationService;
    private static final long TEST_CHANNEL_ID = 9999L;
    
    @BeforeAll
    static void setup() {
        cassandraClient = new CassandraClient("localhost", 9042);
        cassandraClient.initializeSchema();
        paginationService = new PaginationService(cassandraClient.getSession());
        
        // Insert test data
        cassandraClient.bulkInsert(TEST_CHANNEL_ID, 500);
    }
    
    @AfterAll
    static void teardown() {
        cassandraClient.close();
    }
    
    @Test
    @Order(1)
    void testInitialPageLoad() {
        PageResult result = paginationService.fetchPage(TEST_CHANNEL_ID, null, 50, PaginationService.Direction.NEXT);
        
        assertEquals(50, result.messages().size());
        assertTrue(result.hasMore());
        assertNotNull(result.nextCursor());
        assertNull(result.previousCursor());
    }
    
    @Test
    @Order(2)
    void testNextPagePagination() {
        PageResult page1 = paginationService.fetchPage(TEST_CHANNEL_ID, null, 50, PaginationService.Direction.NEXT);
        PageResult page2 = paginationService.fetchPage(TEST_CHANNEL_ID, page1.nextCursor(), 50, PaginationService.Direction.NEXT);
        
        assertEquals(50, page2.messages().size());
        
        // Verify no overlap
        long lastIdPage1 = page1.messages().get(page1.messages().size() - 1).messageId();
        long firstIdPage2 = page2.messages().get(0).messageId();
        assertTrue(firstIdPage2 < lastIdPage1, "Page 2 should have older messages");
    }
    
    @Test
    @Order(3)
    void testBidirectionalPagination() {
        PageResult page1 = paginationService.fetchPage(TEST_CHANNEL_ID, null, 50, PaginationService.Direction.NEXT);
        PageResult page2 = paginationService.fetchPage(TEST_CHANNEL_ID, page1.nextCursor(), 50, PaginationService.Direction.NEXT);
        PageResult backToPage1 = paginationService.fetchPage(TEST_CHANNEL_ID, page2.previousCursor(), 50, PaginationService.Direction.PREVIOUS);
        
        assertEquals(page1.messages().get(0).messageId(), backToPage1.messages().get(0).messageId());
    }
    
    @Test
    @Order(4)
    void testLastPage() {
        String cursor = null;
        PageResult result;
        int pageCount = 0;
        
        do {
            result = paginationService.fetchPage(TEST_CHANNEL_ID, cursor, 50, PaginationService.Direction.NEXT);
            cursor = result.nextCursor();
            pageCount++;
        } while (result.hasMore() && pageCount < 20);
        
        assertFalse(result.hasMore(), "Last page should have hasMore=false");
        assertNull(result.nextCursor(), "Last page should have null nextCursor");
    }
}
