package testing;

import banking.BankingService;
import model.BankAccount;
import model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BankingTests – automated validation suite for BankSync.
 *
 * Each test method:
 *   1. Creates fresh account(s).
 *   2. Performs operations.
 *   3. Asserts expected outcomes.
 *   4. Prints PASS or FAIL with details.
 *
 * Tests cover:
 *   Basic operations : deposit, withdrawal, insufficient-balance, invalid amount,
 *                      balance enquiry, successful/failed transfers.
 *   Concurrency      : multiple concurrent deposits, multiple concurrent withdrawals,
 *                      race-condition reproduction, synchronization correctness,
 *                      deadlock detection, deadlock prevention.
 */
public class BankingTests {

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private static int passed = 0;
    private static int failed = 0;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void run() {
        passed = 0;
        failed = 0;

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  BANKSYNC AUTOMATED TEST SUITE");
        System.out.println("=".repeat(60));

        testDeposit();
        testWithdrawal();
        testInsufficientBalance();
        testInvalidAmount();
        testBalanceEnquiry();
        testSuccessfulTransfer();
        testFailedTransfer();
        testConcurrentDeposits();
        testConcurrentWithdrawals();
        testRaceConditionReproduction();
        testSynchronizationCorrectness();
        testDeadlockDetection();
        testDeadlockPrevention();

        System.out.println("=".repeat(60));
        System.out.printf("  RESULTS:  %d PASSED  |  %d FAILED  |  %d TOTAL%n",
                passed, failed, passed + failed);
        System.out.println("=".repeat(60));
    }

    // =======================================================================
    // 1. DEPOSIT
    // =======================================================================
    private static void testDeposit() {
        BankingService service = new BankingService();
        BankAccount    account = new BankAccount("Test-Deposit", 1_000L);

        Transaction txn = service.deposit(account, 500L);

        assertEqual("Deposit – status",   Transaction.Status.SUCCESS, txn.getStatus(), "testDeposit-status");
        assertEqual("Deposit – balance",  1_500L, account.getBalance(),                "testDeposit-balance");
    }

    // =======================================================================
    // 2. WITHDRAWAL
    // =======================================================================
    private static void testWithdrawal() {
        BankingService service = new BankingService();
        BankAccount    account = new BankAccount("Test-Withdrawal", 1_000L);

        Transaction txn = service.withdraw(account, 300L);

        assertEqual("Withdrawal – status",  Transaction.Status.SUCCESS, txn.getStatus(), "testWithdrawal-status");
        assertEqual("Withdrawal – balance", 700L, account.getBalance(),                  "testWithdrawal-balance");
    }

    // =======================================================================
    // 3. INSUFFICIENT BALANCE
    // =======================================================================
    private static void testInsufficientBalance() {
        BankingService service = new BankingService();
        BankAccount    account = new BankAccount("Test-InsufficientBalance", 200L);

        Transaction txn = service.withdraw(account, 500L);

        assertEqual("InsufficientBalance – status",  Transaction.Status.FAILED, txn.getStatus(), "testInsuf-status");
        assertEqual("InsufficientBalance – balance", 200L, account.getBalance(),                 "testInsuf-balance");
    }

    // =======================================================================
    // 4. INVALID AMOUNT
    // =======================================================================
    private static void testInvalidAmount() {
        BankingService service = new BankingService();
        BankAccount    account = new BankAccount("Test-InvalidAmount", 1_000L);

        // A zero or negative amount must fail.
        Transaction txn = service.deposit(account, -100L);

        assertEqual("InvalidAmount – status",  Transaction.Status.FAILED, txn.getStatus(), "testInvalid-status");
        assertEqual("InvalidAmount – balance", 1_000L, account.getBalance(),               "testInvalid-balance");
    }

    // =======================================================================
    // 5. BALANCE ENQUIRY
    // =======================================================================
    private static void testBalanceEnquiry() {
        BankingService service = new BankingService();
        BankAccount    account = new BankAccount("Test-BalanceEnquiry", 7_500L);

        Transaction txn = service.balanceEnquiry(account);

        assertEqual("BalanceEnquiry – status",  Transaction.Status.SUCCESS, txn.getStatus(), "testBal-status");
        assertTrue ("BalanceEnquiry – notes contains balance",
                txn.getNotes().contains("7,500") || txn.getNotes().contains("7500"),         "testBal-notes");
        assertEqual("BalanceEnquiry – balance unchanged", 7_500L, account.getBalance(),      "testBal-unchanged");
    }

    // =======================================================================
    // 6. SUCCESSFUL FUND TRANSFER
    // =======================================================================
    private static void testSuccessfulTransfer() {
        BankingService service = new BankingService();
        BankAccount    alice   = new BankAccount("Test-Alice", 5_000L);
        BankAccount    bob     = new BankAccount("Test-Bob",   2_000L);

        Transaction txn = service.transfer(alice, bob, 1_000L);

        assertEqual("Transfer – status",          Transaction.Status.SUCCESS, txn.getStatus(), "testXfer-status");
        assertEqual("Transfer – Alice balance",   4_000L, alice.getBalance(),                  "testXfer-alice");
        assertEqual("Transfer – Bob balance",     3_000L, bob.getBalance(),                    "testXfer-bob");
        assertEqual("Transfer – total preserved", 7_000L, alice.getBalance() + bob.getBalance(),"testXfer-total");
    }

    // =======================================================================
    // 7. FAILED FUND TRANSFER (insufficient funds)
    // =======================================================================
    private static void testFailedTransfer() {
        BankingService service = new BankingService();
        BankAccount    alice   = new BankAccount("Test-Alice-Fail", 500L);
        BankAccount    bob     = new BankAccount("Test-Bob-Fail",   1_000L);

        Transaction txn = service.transfer(alice, bob, 2_000L);

        assertEqual("FailedTransfer – status",          Transaction.Status.FAILED, txn.getStatus(), "testFailXfer-status");
        assertEqual("FailedTransfer – Alice unchanged", 500L,   alice.getBalance(),                  "testFailXfer-alice");
        assertEqual("FailedTransfer – Bob unchanged",   1_000L, bob.getBalance(),                    "testFailXfer-bob");
    }

    // =======================================================================
    // 8. MULTIPLE CONCURRENT DEPOSITS
    //    10 threads each deposit Rs. 1,000 into an account starting at Rs. 10,000.
    //    Expected final balance: Rs. 20,000.
    // =======================================================================
    private static void testConcurrentDeposits() {
        BankingService     service  = new BankingService();
        BankAccount        account  = new BankAccount("Test-ConcDeposit", 10_000L);
        int                threads  = 10;
        long               amount   = 1_000L;
        ExecutorService    executor = Executors.newFixedThreadPool(threads);
        CountDownLatch     done     = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                service.deposit(account, amount);
                done.countDown();
            });
        }

        try { done.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shutdownExecutor(executor);

        long expected = 10_000L + (threads * amount);
        assertEqual("ConcurrentDeposits – final balance", expected, account.getBalance(), "testConcDep");
    }

    // =======================================================================
    // 9. MULTIPLE CONCURRENT WITHDRAWALS
    //    5 threads each withdraw Rs. 1,000 from an account starting at Rs. 10,000.
    //    Expected final balance: Rs. 5,000.
    // =======================================================================
    private static void testConcurrentWithdrawals() {
        BankingService  service  = new BankingService();
        BankAccount     account  = new BankAccount("Test-ConcWithdraw", 10_000L);
        int             threads  = 5;
        long            amount   = 1_000L;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch  done     = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                service.withdraw(account, amount);
                done.countDown();
            });
        }

        try { done.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shutdownExecutor(executor);

        long expected = 10_000L - (threads * amount);
        assertEqual("ConcurrentWithdrawals – final balance", expected, account.getBalance(), "testConcWd");
    }

    // =======================================================================
    // 10. RACE CONDITION REPRODUCTION
    //     Two unsynchronised threads each attempt to subtract Rs. 400 from
    //     Rs. 1,000.  The expected correct result is Rs. 200.
    //
    //     Deterministic coordination strategy — CyclicBarrier after READ:
    //       Both threads read the balance (Rs. 1,000) in Phase 1.
    //       Both call barrier.await() — neither can proceed to Phase 2 until
    //       BOTH have completed their reads.
    //       Both then write independently computed results (Rs. 600 each).
    //       The second write overwrites the first → final balance Rs. 600.
    //
    //     This guarantees the conflicting-write interleaving on every run,
    //     regardless of CPU count or OS thread scheduler behaviour.
    // =======================================================================
    private static void testRaceConditionReproduction() {
        System.out.println("\n  [TEST] Race Condition Reproduction");

        BankAccount   account     = new BankAccount("Race-Test", 1_000L);
        CyclicBarrier readBarrier = new CyclicBarrier(2);   // synchronise after READ
        CountDownLatch done       = new CountDownLatch(2);

        // W1: READ → barrier → WRITE
        Thread w1 = new Thread(() -> {
            try {
                long cur = account.unsafeGetBalance();      // PHASE 1: READ
                readBarrier.await();                        // wait for W2 to also read
                account.unsafeSetBalance(cur - 400);        // PHASE 2: WRITE
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                System.out.println("    [RaceW1] barrier broken");
            } finally {
                done.countDown();
            }
        }, "RaceW1");

        // W2: READ → barrier → WRITE
        Thread w2 = new Thread(() -> {
            try {
                long cur = account.unsafeGetBalance();      // PHASE 1: READ
                readBarrier.await();                        // wait for W1 to also read
                account.unsafeSetBalance(cur - 400);        // PHASE 2: WRITE
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                System.out.println("    [RaceW2] barrier broken");
            } finally {
                done.countDown();
            }
        }, "RaceW2");

        w1.start();
        w2.start();

        try { done.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long finalBalance = account.unsafeGetBalance();
        // Expected incorrect result: Rs. 600 (lost update — not Rs. 200)
        boolean raceConfirmed = (finalBalance != 200L);
        printResult("RaceConditionReproduction – lost update confirmed " +
                "(expected Rs. 200, actual Rs. " + finalBalance + ")", raceConfirmed);
    }

    // =======================================================================
    // 11. SYNCHRONIZATION CORRECTNESS
    //     Same Rs. 1,000 / two Rs. 400 withdrawals, but via safe withdraw().
    //     Final balance must ALWAYS be Rs. 200.
    // =======================================================================
    private static void testSynchronizationCorrectness() {
        final int TRIALS = 10;
        boolean allCorrect = true;

        for (int t = 0; t < TRIALS; t++) {
            BankAccount    account  = new BankAccount("Sync-Test", 1_000L);
            BankingService service  = new BankingService();
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done      = new CountDownLatch(2);

            Thread w1 = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                service.withdraw(account, 400L);
                done.countDown();
            });
            Thread w2 = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                service.withdraw(account, 400L);
                done.countDown();
            });

            w1.start();
            w2.start();
            startGate.countDown();

            try { done.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (account.getBalance() != 200L) {
                allCorrect = false;
                break;
            }
        }

        assertEqual("SynchronizationCorrectness – always Rs. 200 (" + TRIALS + " trials)",
                true, allCorrect, "testSyncCorrect");
    }

    // =======================================================================
    // 12. DEADLOCK DETECTION
    //     Two threads acquire locks in reverse order using tryLock with timeout.
    //     At least one must time out (indicating deadlock was structurally present).
    // =======================================================================
    private static void testDeadlockDetection() {
        System.out.println("\n  [TEST] Deadlock Detection");

        BankAccount accountA = new BankAccount("DL-Alice", 1_000L);
        BankAccount accountB = new BankAccount("DL-Bob",   1_000L);

        boolean[] t1TimedOut = {false};
        boolean[] t2TimedOut = {false};

        CountDownLatch t1HoldsA = new CountDownLatch(1);
        CountDownLatch t2HoldsB = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            accountA.getLock().lock();
            t1HoldsA.countDown();
            try { t2HoldsB.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            try {
                boolean got = accountB.getLock().tryLock(1, TimeUnit.SECONDS);
                if (got) accountB.getLock().unlock();
                else t1TimedOut[0] = true;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { accountA.getLock().unlock(); }
        }, "DL-T1");

        Thread t2 = new Thread(() -> {
            try { t1HoldsA.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            accountB.getLock().lock();
            t2HoldsB.countDown();
            try {
                boolean got = accountA.getLock().tryLock(1, TimeUnit.SECONDS);
                if (got) accountA.getLock().unlock();
                else t2TimedOut[0] = true;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { accountB.getLock().unlock(); }
        }, "DL-T2");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        try { t1.join(6_000); t2.join(6_000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean deadlockPresent = t1TimedOut[0] || t2TimedOut[0];
        if (deadlockPresent) {
            printResult("DeadlockDetection – circular wait confirmed by tryLock timeout", true);
        } else {
            System.out.println("    WARN  DeadlockDetection – deadlock not observed (scheduling-dependent).");
        }
    }

    // =======================================================================
    // 13. DEADLOCK PREVENTION
    //     Two opposing transfers run concurrently with consistent lock ordering.
    //     Both must complete and the total money must be preserved.
    // =======================================================================
    private static void testDeadlockPrevention() {
        BankAccount    accountA  = new BankAccount("Prev-Alice", 10_000L);
        BankAccount    accountB  = new BankAccount("Prev-Bob",   10_000L);
        BankingService service   = new BankingService();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(2);

        Thread p1 = new Thread(() -> {
            try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            service.transfer(accountA, accountB, 3_000L);
            done.countDown();
        }, "Prev-P1");

        Thread p2 = new Thread(() -> {
            try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            service.transfer(accountB, accountA, 2_000L);
            done.countDown();
        }, "Prev-P2");

        p1.start();
        p2.start();
        startGate.countDown();

        try { done.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long total = accountA.getBalance() + accountB.getBalance();
        assertEqual("DeadlockPrevention – total money preserved", 20_000L, total, "testDLPrev-total");
        // After A->B Rs.3000 and B->A Rs.2000: A=9000, B=11000
        assertEqual("DeadlockPrevention – Account A balance", 9_000L,  accountA.getBalance(), "testDLPrev-A");
        assertEqual("DeadlockPrevention – Account B balance", 11_000L, accountB.getBalance(), "testDLPrev-B");
    }

    // =======================================================================
    // Assertion helpers
    // =======================================================================

    private static <T> void assertEqual(String label, T expected, T actual, String id) {
        boolean ok = expected.equals(actual);
        printResult(label + " (expected=" + expected + ", actual=" + actual + ")", ok);
    }

    private static void assertTrue(String label, boolean condition, String id) {
        printResult(label, condition);
    }

    private static void printResult(String label, boolean pass) {
        if (pass) {
            System.out.printf("    PASS  %s%n", label);
            passed++;
        } else {
            System.out.printf("  **FAIL  %s%n", label);
            failed++;
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------
    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
