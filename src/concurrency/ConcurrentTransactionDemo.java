package concurrency;

import banking.BankingService;
import model.BankAccount;
import model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * ConcurrentTransactionDemo – runs a realistic mix of concurrent banking operations.
 *
 * Five customer accounts perform simultaneous deposits, withdrawals,
 * balance enquiries, and fund transfers using a fixed thread pool.
 *
 * Key points:
 *   - ExecutorService manages the thread pool lifecycle.
 *   - Each task is a TransactionWorker (Callable<Transaction>).
 *   - Future.get() is used to retrieve each result once it completes.
 *   - All operations use BankingService which is thread-safe.
 *   - Genuine concurrency: tasks execute in parallel, not sequentially.
 */
public class ConcurrentTransactionDemo {

    public static void run(BankAccount[] accounts, BankingService service) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  CONCURRENT TRANSACTION DEMONSTRATION");
        System.out.println("=".repeat(60));
        System.out.println("Submitting 12 concurrent banking tasks across 5 accounts");
        System.out.println("using a thread pool of 4 worker threads.");
        System.out.println("-".repeat(60));

        // Print initial balances.
        System.out.println("Initial balances:");
        for (BankAccount a : accounts) {
            System.out.printf("  %s%n", a);
        }
        System.out.println();

        // Build a list of tasks (mix of operations on different accounts).
        List<TransactionWorker> tasks = new ArrayList<>();

        // Deposits
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.DEPOSIT,
                accounts[0], 2_000L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.DEPOSIT,
                accounts[1], 1_500L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.DEPOSIT,
                accounts[2], 3_000L));

        // Withdrawals
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.WITHDRAW,
                accounts[0], 1_000L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.WITHDRAW,
                accounts[3], 2_500L));

        // Balance enquiries
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.BALANCE_ENQUIRY,
                accounts[1], 0));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.BALANCE_ENQUIRY,
                accounts[4], 0));

        // Fund transfers
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.TRANSFER,
                accounts[0], accounts[2], 500L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.TRANSFER,
                accounts[3], accounts[4], 1_000L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.TRANSFER,
                accounts[2], accounts[1], 750L));

        // Extra concurrent deposits on account[0] to show real concurrency.
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.DEPOSIT,
                accounts[0], 300L));
        tasks.add(new TransactionWorker(service, TransactionWorker.Operation.WITHDRAW,
                accounts[0], 200L));

        // Submit all tasks to a 4-thread pool — genuine parallel execution.
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Transaction>> futures;

        try {
            futures = executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Concurrent demo interrupted.");
            executor.shutdownNow();
            return;
        }

        // Collect and display results.
        System.out.println("Transaction results:");
        int idx = 0;
        for (Future<Transaction> f : futures) {
            try {
                Transaction txn = f.get();
                System.out.printf("  %s%n", txn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("  Task " + idx + " was interrupted.");
            } catch (ExecutionException e) {
                System.out.println("  Task " + idx + " threw: " + e.getCause().getMessage());
            }
            idx++;
        }

        // Shutdown executor cleanly.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println();
        System.out.println("Final balances:");
        for (BankAccount a : accounts) {
            System.out.printf("  %s%n", a);
        }
        System.out.println("=".repeat(60));
    }
}
