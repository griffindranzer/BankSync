package concurrency;

import model.BankAccount;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DeadlockDemo – controlled, isolated demonstration of a circular-wait deadlock.
 *
 * Four necessary conditions for deadlock (Coffman, 1971):
 *
 *   1. Mutual exclusion   — only one thread may hold a lock at a time.
 *   2. Hold and wait      — a thread holds one lock while waiting for another.
 *   3. No preemption      — locks cannot be forcibly taken; a thread must release voluntarily.
 *   4. Circular wait      — Thread A waits for Thread B's lock; Thread B waits for Thread A's.
 *
 * Scenario:
 *   Thread T1 locks Account A, then tries to lock Account B.
 *   Thread T2 locks Account B, then tries to lock Account A.
 *   A CountDownLatch guarantees that T1 holds A before T2 acquires B,
 *   and vice versa, so circular wait is reliably reproduced.
 *
 * Safety:
 *   Both threads run as daemon threads and use tryLock() with a timeout
 *   instead of an indefinite lock().  If the lock cannot be acquired within
 *   the timeout, the thread detects the stall, releases its own lock, and
 *   reports deadlock — the application is never permanently stuck.
 */
public class DeadlockDemo {

    public static void run() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  DEADLOCK DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Creating two accounts and two threads that acquire");
        System.out.println("the locks in OPPOSITE order — this guarantees circular wait.");
        System.out.println();
        System.out.println("Four deadlock conditions that will be satisfied:");
        System.out.println("  1. Mutual exclusion : ReentrantLock — exclusive access.");
        System.out.println("  2. Hold and wait    : Each thread holds one lock while");
        System.out.println("                        waiting for the second.");
        System.out.println("  3. No preemption    : Locks cannot be stolen.");
        System.out.println("  4. Circular wait    : T1->A->B, T2->B->A.");
        System.out.println("-".repeat(60));

        BankAccount accountA = new BankAccount("Alice (Deadlock)", 5_000L);
        BankAccount accountB = new BankAccount("Bob   (Deadlock)", 5_000L);

        System.out.printf("Account A : #%d%n", accountA.getAccountNumber());
        System.out.printf("Account B : #%d%n", accountB.getAccountNumber());
        System.out.println();

        // Phase latches: ensure T1 holds A before T2 tries A, and vice versa.
        CountDownLatch t1HoldsA = new CountDownLatch(1);
        CountDownLatch t2HoldsB = new CountDownLatch(1);

        // Result flags (written from child threads, read from main after join).
        boolean[] t1Deadlocked = {false};
        boolean[] t2Deadlocked = {false};

        // ---- Thread T1: acquires A then tries B ----
        Thread t1 = new Thread(() -> {
            ReentrantLock lockA = accountA.getLock();
            ReentrantLock lockB = accountB.getLock();

            lockA.lock();
            System.out.printf("[T1] Acquired lock on Account A (#%d).%n",
                    accountA.getAccountNumber());
            t1HoldsA.countDown();   // signal: T1 now holds A

            // Wait until T2 also holds its first lock before we try the second.
            try { t2HoldsB.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lockA.unlock();
                return;
            }

            System.out.printf("[T1] Now trying to acquire Account B (#%d) — will block...%n",
                    accountB.getAccountNumber());

            try {
                // tryLock with timeout — detects deadlock without hanging forever.
                boolean acquired = lockB.tryLock(2, TimeUnit.SECONDS);
                if (acquired) {
                    try {
                        System.out.println("[T1] Acquired Account B lock (no deadlock this run).");
                    } finally {
                        lockB.unlock();
                    }
                } else {
                    t1Deadlocked[0] = true;
                    System.out.println("[T1] *** TIMED OUT waiting for Account B — DEADLOCK DETECTED. ***");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lockA.unlock();
                System.out.printf("[T1] Released lock on Account A (#%d).%n",
                        accountA.getAccountNumber());
            }
        }, "T1-AliceToB");
        t1.setDaemon(true);     // daemon: won't prevent JVM shutdown if something goes wrong

        // ---- Thread T2: acquires B then tries A ----
        Thread t2 = new Thread(() -> {
            ReentrantLock lockA = accountA.getLock();
            ReentrantLock lockB = accountB.getLock();

            // Wait until T1 holds A before T2 acquires B.
            try { t1HoldsA.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            lockB.lock();
            System.out.printf("[T2] Acquired lock on Account B (#%d).%n",
                    accountB.getAccountNumber());
            t2HoldsB.countDown();   // signal: T2 now holds B

            System.out.printf("[T2] Now trying to acquire Account A (#%d) — will block...%n",
                    accountA.getAccountNumber());

            try {
                boolean acquired = lockA.tryLock(2, TimeUnit.SECONDS);
                if (acquired) {
                    try {
                        System.out.println("[T2] Acquired Account A lock (no deadlock this run).");
                    } finally {
                        lockA.unlock();
                    }
                } else {
                    t2Deadlocked[0] = true;
                    System.out.println("[T2] *** TIMED OUT waiting for Account A — DEADLOCK DETECTED. ***");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lockB.unlock();
                System.out.printf("[T2] Released lock on Account B (#%d).%n",
                        accountB.getAccountNumber());
            }
        }, "T2-BobToA");
        t2.setDaemon(true);

        t1.start();
        t2.start();

        // Allow up to 6 seconds for both threads to finish (they time out at 2 s each).
        try {
            t1.join(6_000);
            t2.join(6_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println();
        System.out.println("-".repeat(60));
        System.out.println("DEADLOCK SUMMARY:");
        System.out.printf("  Thread T1 deadlocked: %s%n", t1Deadlocked[0] ? "YES" : "NO");
        System.out.printf("  Thread T2 deadlocked: %s%n", t2Deadlocked[0] ? "YES" : "NO");

        if (t1Deadlocked[0] || t2Deadlocked[0]) {
            System.out.println("  *** DEADLOCK WAS REPRODUCED SUCCESSFULLY ***");
            System.out.println("  The tryLock timeout saved the application from hanging.");
        } else {
            System.out.println("  Deadlock did not occur in this run (scheduling variation).");
            System.out.println("  The circular-wait structure is present in the code;");
            System.out.println("  whether it manifests depends on thread scheduling.");
        }
        System.out.println("=".repeat(60));
    }
}
