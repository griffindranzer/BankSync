package concurrency;

import model.BankAccount;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * RaceConditionDemo – controlled, deterministic demonstration of a lost-update
 * race condition.
 *
 * SCENARIO:
 *   Initial balance : Rs. 1,000
 *   Thread W1 "withdraws" Rs. 400  (via unsafe, unsynchronised operations)
 *   Thread W2 "withdraws" Rs. 400  (via unsafe, unsynchronised operations)
 *   Correct balance after both withdrawals : Rs. 200
 *   Actual balance due to race            : Rs. 600
 *
 * WHY THE RACE PRODUCES Rs. 600:
 *   Both threads read the balance (Rs. 1,000) BEFORE either has written.
 *   Each then independently computes 1,000 − 400 = 600 and writes Rs. 600.
 *   The second write overwrites the first; one withdrawal is silently lost.
 *
 * HOW DETERMINISM IS ACHIEVED (CyclicBarrier):
 *   Phase 1 — both threads READ the balance.
 *   Phase 2 — barrier.await(): neither thread may proceed until BOTH have read.
 *             This guarantees both hold the same stale value before either writes.
 *   Phase 3 — both threads WRITE their independently computed result.
 *
 *   Because neither thread can skip ahead to its write before the other has
 *   completed its read, the conflicting-write interleaving is 100% reproducible
 *   regardless of CPU count or OS scheduler behaviour.
 *
 * ISOLATION:
 *   Only unsafeGetBalance() and unsafeSetBalance() are used here.
 *   The safe deposit(), withdraw(), and getBalance() methods — which acquire
 *   the account's ReentrantLock — are never called in this class.
 */
public class RaceConditionDemo {

    public static void run() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  RACE CONDITION DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Scenario : Two threads each withdraw Rs. 400 from");
        System.out.println("           an account starting at Rs. 1,000.");
        System.out.println("Expected : Rs. 200   (both withdrawals succeed)");
        System.out.println("Race bug : Both threads read the same initial balance");
        System.out.println("           and each overwrites the other's update —");
        System.out.println("           a classic lost-update error.");
        System.out.println();
        System.out.println("Coordination: CyclicBarrier after READ phase.");
        System.out.println("  Both threads read BEFORE either writes.");
        System.out.println("  Both compute 1,000 - 400 = 600.");
        System.out.println("  Both write 600 — second write obliterates the first.");
        System.out.println("-".repeat(60));

        BankAccount account = new BankAccount("Race-Demo Account", 1_000L);
        System.out.printf("Initial balance: Rs. %,d%n%n", account.unsafeGetBalance());

        // ---- Shared result storage ----
        long[] w1Read  = {0};
        long[] w2Read  = {0};

        // CyclicBarrier with 2 parties.
        // Both threads call barrier.await() after their READ.
        // The JVM releases both only once both have checked in.
        // This is the synchronisation point that makes the interleaving deterministic.
        CyclicBarrier readBarrier = new CyclicBarrier(2);

        // startGate ensures both threads are fully initialised before the demo starts.
        CountDownLatch startGate = new CountDownLatch(1);

        // ---- Thread W1 ----
        Thread w1 = new Thread(() -> {
            try {
                startGate.await();

                // ---- PHASE 1: READ ----
                long readBalance = account.unsafeGetBalance();
                w1Read[0] = readBalance;
                System.out.printf("[%s] PHASE 1 — READ  balance = Rs. %,d%n",
                        Thread.currentThread().getName(), readBalance);

                // ---- BARRIER: wait until W2 has also read ----
                // Neither thread can proceed to WRITE until BOTH have read.
                readBarrier.await();

                // ---- PHASE 2: WRITE ----
                long newBalance = readBalance - 400;
                account.unsafeSetBalance(newBalance);
                System.out.printf("[%s] PHASE 2 — WROTE balance = Rs. %,d  (computed %,d - 400)%n",
                        Thread.currentThread().getName(),
                        account.unsafeGetBalance(), readBalance);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                System.out.println("[W1] Barrier broken — demo aborted.");
            }
        }, "Withdrawer-W1");

        // ---- Thread W2 ----
        Thread w2 = new Thread(() -> {
            try {
                startGate.await();

                // ---- PHASE 1: READ ----
                long readBalance = account.unsafeGetBalance();
                w2Read[0] = readBalance;
                System.out.printf("[%s] PHASE 1 — READ  balance = Rs. %,d%n",
                        Thread.currentThread().getName(), readBalance);

                // ---- BARRIER: wait until W1 has also read ----
                readBarrier.await();

                // ---- PHASE 2: WRITE ----
                long newBalance = readBalance - 400;
                account.unsafeSetBalance(newBalance);
                System.out.printf("[%s] PHASE 2 — WROTE balance = Rs. %,d  (computed %,d - 400)%n",
                        Thread.currentThread().getName(),
                        account.unsafeGetBalance(), readBalance);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                System.out.println("[W2] Barrier broken — demo aborted.");
            }
        }, "Withdrawer-W2");

        w1.start();
        w2.start();
        startGate.countDown();      // release both threads

        try {
            w1.join();
            w2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long finalBalance = account.unsafeGetBalance();

        System.out.println();
        System.out.println("Analysis:");
        System.out.printf("  W1 read : Rs. %,d%n", w1Read[0]);
        System.out.printf("  W2 read : Rs. %,d%n", w2Read[0]);
        System.out.printf("  Both computed : Rs. 1,000 - 400 = Rs. 600%n");
        System.out.printf("  W1 wrote Rs. 600, W2 wrote Rs. 600 — one update lost.%n");
        System.out.println();
        System.out.printf("  Actual final balance   : Rs. %,d%n", finalBalance);
        System.out.printf("  Expected final balance : Rs. %,d%n", 200L);

        if (finalBalance != 200L) {
            System.out.println();
            System.out.println("  *** RACE CONDITION CONFIRMED ***");
            System.out.printf("  Lost update: balance is Rs. %,d instead of Rs. 200.%n", finalBalance);
            System.out.println("  One withdrawal was silently overwritten.");
        } else {
            System.out.println("  (Unexpected: writes did not conflict — rerun the demo.)");
        }

        System.out.println();
        System.out.println("Root cause:");
        System.out.println("  The read-then-write sequence is NOT atomic.");
        System.out.println("  Without a lock, two threads can interleave:");
        System.out.println("    Thread 1 reads  → Thread 2 reads  (same stale value)");
        System.out.println("    Thread 1 writes → Thread 2 writes (overwrites Thread 1)");
        System.out.println("  The fix is in SynchronizationDemo: a ReentrantLock makes");
        System.out.println("  the entire read-check-write sequence indivisible.");
        System.out.println("=".repeat(60));
    }
}
