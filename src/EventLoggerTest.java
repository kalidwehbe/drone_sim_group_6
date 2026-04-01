import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/**
 * Unit tests for EventLogger iteration 4 addition
 * Tests that events are logged correctly to both the console and event_log.txt (text file)
 */
public class EventLoggerTest {

    private static final String LOG_FILE = "event_log.txt";

    @Before
    public void setUp() throws IOException {
        // Clears the log file before each test
        Files.deleteIfExists(Paths.get(LOG_FILE));
        EventLogger.clearLog();
    }

    @After
    public void tearDown() throws IOException {
        // Cleans up after each test
        Files.deleteIfExists(Paths.get(LOG_FILE));
    }

    @Test
    public void testLogCreatesFile() {
        EventLogger.log("TEST", "EVENT", "test message");

        // Verifying that the file was created
        assertTrue("Log file should exist", Files.exists(Paths.get(LOG_FILE)));
    }

    @Test
    public void testLogAppendsToFile() throws IOException {
        EventLogger.log("TEST", "EVENT1", "first message");
        EventLogger.log("TEST", "EVENT2", "second message");

        // Reading the file and verifying that both messages exist
        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
            String content = reader.lines().reduce("", (a, b) -> a + b);
            assertTrue(content.contains("EVENT1"));
            assertTrue(content.contains("EVENT2"));
        }
    }

    @Test
    public void testLogFormat() throws IOException {
        EventLogger.log("SCHEDULER", "FAULT_DETECTED", "drone=1 reason=NOZZLE_FAULT");

        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
            String line = reader.readLine();
            // Verifying that the format is: timestamp | subsystem | event | additional info
            assertTrue(line.contains("| SCHEDULER | FAULT_DETECTED |"));
            assertTrue(line.contains("drone=1 reason=NOZZLE_FAULT"));
        }
    }

    @Test
    public void testClearLog() throws IOException {
        EventLogger.log("TEST", "EVENT", "message");
        assertTrue(Files.exists(Paths.get(LOG_FILE)));

        EventLogger.clearLog();

        // The file should be cleared
        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
            assertNull("File should be empty after clear", reader.readLine());
        }
    }
}