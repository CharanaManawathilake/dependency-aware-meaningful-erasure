package org.example;

import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Property;

import java.util.*;

/**
 * Algorithm 3: Reconstruction Scheduler
 *
 * Determines WHEN to reconstruct derived data to minimize recomputations
 * when data expires at different retention times.
 *
 * Instead of recomputing derived data separately for each expiration,
 * the scheduler batches expirations that overlap to reduce redundant work.
 */
public class ReconstructionScheduler {

    /**
     * Represents a retention interval for a cell
     */
    public static class RetentionInterval implements Comparable<RetentionInterval> {
        public Cell cell;
        public long startTime;    // When data was inserted
        public long expiryTime;   // When data must be deleted
        public Property property;
        
        public RetentionInterval(Cell cell, Property property, long startTime, long expiryTime) {
            this.cell = cell;
            this.property = property;
            this.startTime = startTime;
            this.expiryTime = expiryTime;
        }

        @Override
        public int compareTo(RetentionInterval other) {
            return Long.compare(this.expiryTime, other.expiryTime);
        }

        @Override
        public String toString() {
            return String.format("Cell(%s)[%d-%d]", cell.key, startTime, expiryTime);
        }
    }

    /**
     * Represents a scheduled reconstruction event
     */
    public static class ReconstructionEvent {
        public long scheduledTime;           // When to reconstruct
        public Set<Cell> cellsToProcess;    // All cells involved
        public List<RetentionInterval> intervals; // Intervals being handled
        
        public ReconstructionEvent(long scheduledTime) {
            this.scheduledTime = scheduledTime;
            this.cellsToProcess = new HashSet<>();
            this.intervals = new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.format("Reconstruct@%d: %d cells", scheduledTime, cellsToProcess.size());
        }
    }

    private List<RetentionInterval> allIntervals;
    private TreeMap<Long, ReconstructionEvent> schedule;

    public ReconstructionScheduler() {
        this.allIntervals = new ArrayList<>();
        this.schedule = new TreeMap<>();
    }

    /**
     * Add a cell with its retention interval
     */
    public void addCell(Cell cell, Property property, long insertionTime, long expiryTime) {
        allIntervals.add(new RetentionInterval(cell, property, insertionTime, expiryTime));
    }

    /**
     * Core algorithm: Find maximum overlap times and schedule reconstructions
     * 
     * Based on paper: finds times where most retention intervals overlap
     */
    public TreeMap<Long, ReconstructionEvent> generateSchedule() {
        if (allIntervals.isEmpty()) {
            return schedule;
        }

        // Sort by expiry time
        Collections.sort(allIntervals);

        // Create events for critical times (where overlaps change)
        Set<Long> criticalTimes = new HashSet<>();
        for (RetentionInterval interval : allIntervals) {
            criticalTimes.add(interval.startTime);
            criticalTimes.add(interval.expiryTime);
        }

        List<Long> sortedTimes = new ArrayList<>(criticalTimes);
        Collections.sort(sortedTimes);

        // Find maximum overlap regions
        for (int i = 0; i < sortedTimes.size() - 1; i++) {
            long windowStart = sortedTimes.get(i);
            long windowEnd = sortedTimes.get(i + 1);
            long midpoint = (windowStart + windowEnd) / 2;

            // Find all intervals that overlap this window
            List<RetentionInterval> overlappingIntervals = new ArrayList<>();
            for (RetentionInterval interval : allIntervals) {
                if (interval.startTime <= midpoint && midpoint < interval.expiryTime) {
                    overlappingIntervals.add(interval);
                }
            }

            // Only schedule if we have significant overlap
            if (overlappingIntervals.size() >= ConfigParameter.schedulerBatchSize || 
                shouldBatchReconstructAndFlag(overlappingIntervals)) {
                
                ReconstructionEvent event = new ReconstructionEvent(midpoint);
                for (RetentionInterval interval : overlappingIntervals) {
                    event.cellsToProcess.add(interval.cell);
                    event.intervals.add(interval);
                }
                schedule.put(midpoint, event);
            }
        }

        return schedule;
    }

    /**
     * Check if overlapping intervals meet the overlap threshold criterion
     */
    private boolean shouldBatchReconstructAndFlag(List<RetentionInterval> intervals) {
        if (intervals.isEmpty()) return false;

        // Calculate overlap ratio
        long minStart = intervals.stream().mapToLong(i -> i.startTime).min().orElse(0);
        long maxEnd = intervals.stream().mapToLong(i -> i.expiryTime).max().orElse(0);
        long overlapWindow = maxEnd - minStart;

        double avgInterval = intervals.stream()
            .mapToLong(i -> i.expiryTime - i.startTime)
            .average()
            .orElse(0);

        double overlapRatio = avgInterval / overlapWindow;
        return overlapRatio >= ConfigParameter.overlapThreshold;
    }

    /**
     * Get the next scheduled reconstruction event
     */
    public ReconstructionEvent getNextEvent() {
        if (schedule.isEmpty()) return null;
        long nextTime = schedule.firstKey();
        return schedule.remove(nextTime);
    }

    /**
     * Get all scheduled events (for inspection/logging)
     */
    public List<ReconstructionEvent> getAllEvents() {
        return new ArrayList<>(schedule.values());
    }

    /**
     * Print the schedule (for debugging)
     */
    public void printSchedule() {
        System.out.println("\n=== Reconstruction Schedule ===");
        System.out.println("Total intervals: " + allIntervals.size());
        System.out.println("Scheduled events: " + schedule.size());

        if (schedule.isEmpty()) {
            System.out.println("No batching possible - processing individually");
            return;
        }

        for (Map.Entry<Long, ReconstructionEvent> entry : schedule.entrySet()) {
            System.out.println(entry.getValue());
        }

        // Show savings
        int individualRecomputations = allIntervals.size();
        int batchedRecomputations = schedule.size();
        int saved = individualRecomputations - batchedRecomputations;
        double savingsPercent = (saved * 100.0) / individualRecomputations;

        System.out.println("\nSavings: " + saved + " fewer recomputations (" + 
            String.format("%.1f%%", savingsPercent) + ")");
        System.out.println("=============================\n");
    }

    /**
     * Statistics about the schedule
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCells", allIntervals.size());
        stats.put("scheduledEvents", schedule.size());
        stats.put("reductionRatio", schedule.isEmpty() ? 0 : 
            allIntervals.size() / (double) schedule.size());
        return stats;
    }
}
