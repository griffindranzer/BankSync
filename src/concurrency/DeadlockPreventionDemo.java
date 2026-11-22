package concurrency;

import model.BankAccount;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DeadlockPreventionDemo – demonstrates that consistent lock ordering eliminates
 * circular wait even when two threads attempt opposing transfers simultaneously.
 *
 * Prevention strategy: Resource ordering (also called lock ordering).
 *
 *   Rule: Always acquire the lock for the account with the LOWER account number
 *         FIRST, regardless of which direction the transfer is flowing.
 *
 *   Why it works:
 *     Circular wait requires Thread A to hold lock X while waiting for lock Y,
 *     AND Thread B to hold lock Y while waiting for lock X.
 *     With a fixed ordering (always X before Y when X.id < Y.id), no thread
 *     ever holds the "higher" lock while waiting for the "lower" lock.
 *     Therefore circular wait cannot form.
 *
 * Scenario:
 *   Account A (#lower id) starts with Rs. 10,000.
 *   Account B (#higher id) starts with Rs. 10,000.
 *   Thread P1 transfers Rs. 3,000 from A to B.
 *   Thread P2 transfers Rs. 2,000 from B to A.
 *   Both transfer at the same time — but neither ever deadlocks.
 *
 * This is the SAME locking strategy used by BankingService.transfer().
 */
public class DeadlockPreventionDemo {

    public static void run() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  DEADLOCK PREVENTION DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Strategy : Consistent lock ordering (resource ordering).");
        System.out.println("           Always lock lower account-ID first.");
        System.out.println();
        System.out.println("Scenario : Two threads transfer in opposite directions");
        System.out.println("           concurrently — no deadlock occurs.");
        System.out.println("-".repeat(60));

        BankAccount accountA = new BankAccount("Alice (Prevention)", 10_000L);
        BankAccount accountB = new BankAccount("Bob   (Prevention)", 10_000L);

        System.out.printf("Account A : #%d, Initial balance: Rs. %,d%n",
                accountA.getAccountNumber(), accountA.getBalance());
        System.out.printf("Account B : #%d, Initial balance: Rs. %,d%n",
                accountB.getAccountNumber(), accountB.getBalance());
        System.out.println();

        // Both transfers will apply the same lock-ordering rule,
        // so neither can be waiting for a lock that the other holds.
        // Lock ordering: lower account number is always acquired first.
        // Since accountA was created before accountB, A.id < B.id.

        CountDownLatch startGate  = new CountDownLatch(1);
        CountDownLatch allDone    = new CountDownLatch(2);

        // ---- Thread P1: transfer Rs. 3,000 from A to B ----
        Thread p1 = new Thread(() -> {
            try { startGate.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.printf("[%s] Starting transfer: A -> B  Rs. 3,000%n",
                    Thread.currentThread().getName());
            safeTransfer(accountA, accountB, 3_000L);
            allDone.countDown();
        }, "P1-AtoB");

        // ---- Thread P2: transfer Rs. 2,000 from B to A ----
        Thread p2 = new Thread(() -> {
            try { startGate.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.printf("[%s] Starting transfer: B -> A  Rs. 2,000%n",
                    Thread.currentThread().getName());
            safeTransfer(accountB, accountA, 2_000L);
            allDone.countDown();
        }, "P2-BtoA");

        p1.start();
        p2.start();
        startGate.countDown();      // release both threads simultaneously

        try { allDone.await(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println();
        System.out.printf("Final balance A (#%d) : Rs. %,d  (expected Rs. 9,000)%n",
                accountA.getAccountNumber(), accountA.getBalance());
        System.out.printf("Final balance B (#%d) : Rs. %,d  (expected Rs. 11,000)%n",
                accountB.getAccountNumber(), accountB.getBalance());

        long totalAfter = accountA.getBalance() + accountB.getBalance();
        System.out.printf("Total after transfers  : Rs. %,d  (must equal Rs. 20,000)%n", totalAfter);
        System.out.println(totalAfter == 20_000L
                ? "  CORRECT — no money created or lost."
                : "  ERROR — total mismatch; check synchronization.");

        System.out.println();
        System.out.println("Why no deadlock occurred:");
        System.out.println("  P1 (A->B) locks: A first, then B.  (A.id < B.id — ordered)");
        System.out.println("  P2 (B->A) locks: A first, then B.  (A.id < B.id — ordered)");
        System.out.println("  Both threads acquire locks in the same order, so at worst");
        System.out.println("  P2 blocks until P1 releases A.  P1 never waits for a lock");
        System.out.println("  that P2 holds, so circular wait cannot form.");
        System.out.println("=".repeat(60));
    }

    /**
     * Transfers {@code amount} from {@code source} to {@code destination}
     * using consistent lock ordering to prevent deadlock.
     *
     * Lock ordering rule: always lock the account with the lower account number
     * first.  This is the same rule applied by BankingService.transfer().
     */
    private static void safeTransfer(BankAccount source, BankAccount destination, long amount) {
        String name = Thread.currentThread().getName();

        // Determine which account to lock first (lower ID wins).
        BankAccount first  = (source.getAccountNumber() < destination.getAccountNumber())
                ? source : destination;
        BankAccount second = (first == source) ? destination : source;

        ReentrantLock lockFirst  = first.getLock();
        ReentrantLock lockSecond = second.getLock();

        System.out.printf("[%s] Lock order: Account #%d (first) -> Account #%d (second)%n",
                name, first.getAccountNumber(), second.getAccountNumber());

        lockFirst.lock();
        System.out.printf("[%s] Acquired lock on Account #%d.%n",
                name, first.getAccountNumber());
        try {
            lockSecond.lock();
            System.out.printf("[%s] Acquired lock on Account #%d.%n",
                    name, second.getAccountNumber());
            try {
                boolean ok = source.withdrawUnderLock(amount);
                if (ok) {
                    destination.depositUnderLock(amount);
                    System.out.printf("[%s] Transfer complete: Rs. %,d  |  " +
                                    "A bal Rs. %,d  |  B bal Rs. %,d%n",
                            name, amount, source.getBalance(), destination.getBalance());
                } else {
                    System.out.printf("[%s] Transfer failed: insufficient funds.%n", name);
                }
            } finally {
                lockSecond.unlock();
                System.out.printf("[%s] Released lock on Account #%d.%n",
                        name, second.getAccountNumber());
            }
        } finally {
            lockFirst.unlock();
            System.out.printf("[%s] Released lock on Account #%d.%n",
                    name, first.getAccountNumber());
        }
    }
}
