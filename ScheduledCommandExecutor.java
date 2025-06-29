import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class ScheduledCommandExecutor {
    private final String filePath;
    private final List<OneTimeJob> oneTimeJobs = new ArrayList<>();
    private final List<RecurringJob> recurringJobs = new ArrayList<>();

    public ScheduledCommandExecutor(String filePath) {
        this.filePath = filePath;
    }

    public void start() {
        loadCommands();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkAndExecute, 0, 1, TimeUnit.MINUTES);
    }

    private void loadCommands() {
        oneTimeJobs.clear();
        recurringJobs.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
                if (line.startsWith("*/")) {
                    String[] parts = line.split(" ", 2);
                    int interval = Integer.parseInt(parts[0].substring(2));
                    recurringJobs.add(new RecurringJob(interval, parts[1]));
                } else {
                    String[] parts = line.split(" ", 6);
                    int min = Integer.parseInt(parts[0]);
                    int hour = Integer.parseInt(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    int month = Integer.parseInt(parts[3]);
                    int year = Integer.parseInt(parts[4]);
                    String command = parts[5];
                    LocalDateTime time = LocalDateTime.of(year, month, day, hour, min);
                    oneTimeJobs.add(new OneTimeJob(time, command));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load commands: " + e.getMessage());
        }
    }

    private void checkAndExecute() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        for (OneTimeJob job : oneTimeJobs) {
            if (!job.executed && now.equals(job.time)) {
                System.out.println("[One-Time] Executing: " + job.command);
                executeCommand(job.command);
                job.executed = true;
            }
        }

        for (RecurringJob job : recurringJobs) {
            long minutes = Duration.between(job.lastRun, now).toMinutes();
            if (minutes >= job.interval && now.getMinute() % job.interval == 0) {
                System.out.println("[Recurring every " + job.interval + "m] Executing: " + job.command);
                executeCommand(job.command);
                job.lastRun = now;
            }
        }
    }

    private void executeCommand(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-c", command).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.lines().forEach(System.out::println);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Execution failed: " + e.getMessage());
        }
    }

    private static class OneTimeJob {
        LocalDateTime time;
        String command;
        boolean executed = false;

        OneTimeJob(LocalDateTime time, String command) {
            this.time = time;
            this.command = command;
        }
    }

    private static class RecurringJob {
        int interval;
        String command;
        LocalDateTime lastRun = LocalDateTime.MIN;

        RecurringJob(int interval, String command) {
            this.interval = interval;
            this.command = command;
        }
    }

    public static void main(String[] args) {
        ScheduledCommandExecutor executor = new ScheduledCommandExecutor("/tmp/commands.txt");
        executor.start();
    }
}
