package concurrency;

import java.util.concurrent.Semaphore;

/**
 * SemaphoreDemo – mutual exclusion using Java's Semaphore.
 *
 * A binary semaphore (permits = 1) acts like a mutex:
 *   acquire() — if the permit is available, take it and proceed;
 *               otherwise BLOCK until another thread calls release().
 *   release() — return the permit, unblocking one waiting thread.
 *
 * This demo simulates five ATM threads all competing for access to
 * a single shared ATM cash-dispenser resource.
 */
public class SemaphoreDemo {

    // A binary semaphore (1 permit) models exclusive access to one ATM.
    // Fair = true means threads are granted access in arrival order (FIFO),
    // which makes the output more predictable and prevents starvation.
    private static final Semaphore ATM_SEMAPHORE = new Semaphore(1, true);

    // Simulated shared state: cash remaining in the dispenser.
    private static long cashRemaining = 10_000L;

    public static void run() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  SEMAPHORE DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Scenario : 5 ATM customer threads compete for a single");
        System.out.println("           shared ATM cash-dispenser (binary semaphore).");
        System.out.println("Semaphore: permits = 1, fair = true (FIFO ordering).");
        System.out.println("-".repeat(60));

        cashRemaining = 10_000L;
        System.out.printf("ATM loaded with Rs. %,d%n%n", cashRemaining);

        Thread[] customers = new Thread[5];
        long[]   amounts   = { 2_000L, 1_500L, 3_000L, 1_000L, 2_500L };

        for (int i = 0; i < customers.length; i++) {
            final long withdrawAmount = amounts[i];
            customers[i] = new Thread(() -> accessAtm(withdrawAmount),
                    "ATM-Customer-" + (i + 1));
        }

        // Start all threads at approximately the same time.
        for (Thread c : customers) c.start();

        // Wait for all to finish.
        for (Thread c : customers) {
            try { c.join(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("-".repeat(60));
        System.out.printf("ATM cash remaining : Rs. %,d%n", cashRemaining);
        System.out.println();
        System.out.println("Key observations:");
        System.out.println("  1. At most ONE thread was inside the critical section at a time.");
        System.out.println("  2. Each thread printed 'ACQUIRED' before its operation and");
        System.out.println("     'RELEASED' after — proving sequential ATM access.");
        System.out.println("  3. No two threads overlapped inside the dispenser section.");
        System.out.println("  4. The semaphore prevented concurrent balance corruption.");
        System.out.println("=".repeat(60));
    }

    /**
     * Simulates one customer's ATM session.
     * acquire() / release() bracket the critical section.
     */
    private static void accessAtm(long amount) {
        String name = Thread.currentThread().getName();
        System.out.printf("[%s] Waiting for ATM access (wants Rs. %,d)...%n", name, amount);

        // --- ACQUIRE: take the single permit (block if another thread holds it) ---
        try {
            ATM_SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("[%s] Interrupted while waiting.%n", name);
            return;
        }

        // ==================== CRITICAL SECTION BEGIN ==========================
        try {
            System.out.printf("[%s] >> SEMAPHORE ACQUIRED — entering ATM.%n", name);

            // Simulate ATM processing time.
            Thread.sleep(100);

            if (cashRemaining >= amount) {
                cashRemaining -= amount;
                System.out.printf("[%s] Dispensed Rs. %,d. Remaining: Rs. %,d%n",
                        name, amount, cashRemaining);
            } else {
                System.out.printf("[%s] Insufficient cash (Rs. %,d available, Rs. %,d requested). No dispensing.%n",
                        name, cashRemaining, amount);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // --- RELEASE: return the permit (always, even if an exception occurred) ---
            ATM_SEMAPHORE.release();
            System.out.printf("[%s] << SEMAPHORE RELEASED — leaving ATM.%n", name);
            // ==================== CRITICAL SECTION END ============================
        }
    }
}
