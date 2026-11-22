package concurrency;

import model.BankAccount;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * SynchronizationDemo – the corrected counterpart to RaceConditionDemo.
 *
 * Uses the same scenario (Rs. 1,000, two withdrawals of Rs. 400) but calls
 * BankAccount's safe, synchronised withdraw() method.  Only one thread at a
 * time may execute inside the synchronised block, so the read-modify-write is
 * indivisible and the final balance is always Rs. 200.
 *
 * Key OS concepts demonstrated:
 *   - Mutual exclusion  : at most one thread inside the critical section
 *   - Critical section  : the balance modification
 *   - Thread-safe state : BankAccount.withdraw() is a synchronised method
 */
public class SynchronizationDemo {

    public static void run() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  SYNCHRONIZATION / MUTUAL EXCLUSION DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Same scenario as the race-condition demo, but now");
        System.out.println("BankAccount.withdraw() is a synchronized method.");
        System.out.println();
        System.out.println("  Shared resource   : BankAccount (balance field)");
        System.out.println("  Critical section  : read balance, check, deduct, write");
        System.out.println("  Guard mechanism   : Java 'synchronized' keyword on method");
        System.out.println("-".repeat(60));

        BankAccount account = new BankAccount("Sync-Demo Account", 1_000L);
        System.out.printf("Initial balance: Rs. %,d%n%n", account.getBalance());

        // CyclicBarrier aligns both threads so they attempt to withdraw at the same instant.
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done   = new CountDownLatch(2);

        Thread[] withdrawers = new Thread[2];

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            withdrawers[i] = new Thread(() -> {
                try {
                    barrier.await();    // wait until BOTH threads are ready
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                System.out.printf("[%s] Attempting to withdraw Rs. 400 — entering critical section...%n",
                        Thread.currentThread().getName());

                // ---- CRITICAL SECTION BEGIN ----
                // Only one thread executes this at a time (monitor on 'account').
                boolean success = account.withdraw(400L);
                // ---- CRITICAL SECTION END ------

                if (success) {
                    System.out.printf("[%s] Withdrawal successful. Balance now: Rs. %,d%n",
                            Thread.currentThread().getName(), account.getBalance());
                } else {
                    System.out.printf("[%s] Withdrawal failed — insufficient funds. Balance: Rs. %,d%n",
                            Thread.currentThread().getName(), account.getBalance());
                }
                done.countDown();
            }, "SafeWithdrawer-" + (idx + 1));
        }

        for (Thread t : withdrawers) t.start();

        try { done.await(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println();
        System.out.printf("Final balance  : Rs. %,d%n", account.getBalance());
        System.out.printf("Expected       : Rs. %,d%n", 200L);
        System.out.println(account.getBalance() == 200L
                ? "  CORRECT — synchronization produced the right result."
                : "  UNEXPECTED — balance mismatch; review synchronization.");

        System.out.println();
        System.out.println("Why synchronization works:");
        System.out.println("  'synchronized' on a method acquires the intrinsic lock");
        System.out.println("  (monitor) of the object before entering the method body.");
        System.out.println("  While Thread 1 holds the monitor, Thread 2 blocks at the");
        System.out.println("  method entry and waits.  This guarantees that the entire");
        System.out.println("  read-check-write sequence is never interrupted by another");
        System.out.println("  thread, eliminating the lost-update race condition.");
        System.out.println("=".repeat(60));
    }
}
