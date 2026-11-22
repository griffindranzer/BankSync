import banking.BankingService;
import concurrency.*;
import model.BankAccount;
import model.Transaction;
import testing.BankingTests;

import java.util.Scanner;

/**
 * Main – console entry point for BankSync.
 *
 * Maintains five sample customer accounts and a shared BankingService.
 * Presents a numbered menu; each option invokes the appropriate subsystem.
 * Input validation prevents invalid account selections or malformed amounts
 * from crashing the application.
 */
public class Main {

    // Five sample accounts shared across the session.
    private static final BankAccount[] ACCOUNTS = {
        new BankAccount("Arjun Sharma",    50_000L),
        new BankAccount("Priya Patel",     75_000L),
        new BankAccount("Ravi Verma",      30_000L),
        new BankAccount("Sneha Iyer",      60_000L),
        new BankAccount("Vikram Mehta",    45_000L)
    };

    private static final BankingService SERVICE = new BankingService();
    private static final Scanner        SCANNER = new Scanner(System.in);

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        printWelcome();
        boolean running = true;

        while (running) {
            printMenu();
            int choice = readInt("Enter option: ", 1, 12);

            switch (choice) {
                case 1  -> doDeposit();
                case 2  -> doWithdraw();
                case 3  -> doBalanceEnquiry();
                case 4  -> doTransfer();
                case 5  -> ConcurrentTransactionDemo.run(ACCOUNTS, SERVICE);
                case 6  -> RaceConditionDemo.run();
                case 7  -> SynchronizationDemo.run();
                case 8  -> SemaphoreDemo.run();
                case 9  -> DeadlockDemo.run();
                case 10 -> DeadlockPreventionDemo.run();
                case 11 -> BankingTests.run();
                case 12 -> { running = false; System.out.println("\nThank you for using BankSync. Goodbye!"); }
            }
        }
        SCANNER.close();
    }

    // -----------------------------------------------------------------------
    // Interactive banking operations
    // -----------------------------------------------------------------------

    private static void doDeposit() {
        System.out.println("\n--- DEPOSIT ---");
        BankAccount account = selectAccount();
        if (account == null) return;
        long amount = readAmount("Enter deposit amount (Rs.): ");
        if (amount <= 0) return;

        Transaction txn = SERVICE.deposit(account, amount);
        printTxnResult(txn);
    }

    private static void doWithdraw() {
        System.out.println("\n--- WITHDRAWAL ---");
        BankAccount account = selectAccount();
        if (account == null) return;
        long amount = readAmount("Enter withdrawal amount (Rs.): ");
        if (amount <= 0) return;

        Transaction txn = SERVICE.withdraw(account, amount);
        printTxnResult(txn);
    }

    private static void doBalanceEnquiry() {
        System.out.println("\n--- BALANCE ENQUIRY ---");
        BankAccount account = selectAccount();
        if (account == null) return;

        Transaction txn = SERVICE.balanceEnquiry(account);
        printTxnResult(txn);
    }

    private static void doTransfer() {
        System.out.println("\n--- FUND TRANSFER ---");
        System.out.println("Select SOURCE account:");
        BankAccount source = selectAccount();
        if (source == null) return;

        System.out.println("Select DESTINATION account:");
        BankAccount destination = selectAccount();
        if (destination == null) return;

        if (source.getAccountNumber() == destination.getAccountNumber()) {
            System.out.println("Source and destination cannot be the same account.");
            return;
        }

        long amount = readAmount("Enter transfer amount (Rs.): ");
        if (amount <= 0) return;

        Transaction txn = SERVICE.transfer(source, destination, amount);
        printTxnResult(txn);
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    private static void printWelcome() {
        System.out.println("=".repeat(60));
        System.out.println("           BANKSYNC – Multithreaded Banking System");
        System.out.println("           CSC304A Operating Systems Assignment");
        System.out.println("=".repeat(60));
        System.out.println("Sample customer accounts:");
        for (int i = 0; i < ACCOUNTS.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, ACCOUNTS[i]);
        }
        System.out.println("=".repeat(60));
    }

    private static void printMenu() {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║                     MAIN MENU                       ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  Banking Operations                                  ║");
        System.out.println("║   1. Deposit                                         ║");
        System.out.println("║   2. Withdraw                                        ║");
        System.out.println("║   3. Balance Enquiry                                 ║");
        System.out.println("║   4. Fund Transfer                                   ║");
        System.out.println("║                                                      ║");
        System.out.println("║  Concurrency Demonstrations                          ║");
        System.out.println("║   5. Concurrent Transaction Demonstration            ║");
        System.out.println("║   6. Race Condition Demonstration                    ║");
        System.out.println("║   7. Synchronization Demonstration                   ║");
        System.out.println("║   8. Semaphore Demonstration                         ║");
        System.out.println("║   9. Deadlock Demonstration                          ║");
        System.out.println("║  10. Deadlock Prevention Demonstration               ║");
        System.out.println("║                                                      ║");
        System.out.println("║  Testing                                             ║");
        System.out.println("║  11. Run Banking Tests                               ║");
        System.out.println("║                                                      ║");
        System.out.println("║  12. Exit                                            ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    private static BankAccount selectAccount() {
        System.out.println("Available accounts:");
        for (int i = 0; i < ACCOUNTS.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, ACCOUNTS[i]);
        }
        int idx = readInt("Select account (1-" + ACCOUNTS.length + "): ", 1, ACCOUNTS.length);
        return ACCOUNTS[idx - 1];
    }

    private static void printTxnResult(Transaction txn) {
        System.out.println();
        System.out.println("Transaction result:");
        System.out.println("  " + txn);
    }

    // -----------------------------------------------------------------------
    // Input validation
    // -----------------------------------------------------------------------

    /**
     * Reads an integer in [min, max] from the console.
     * Re-prompts on invalid input; never throws.
     */
    private static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine().trim();
            try {
                int val = Integer.parseInt(line);
                if (val >= min && val <= max) return val;
                System.out.printf("Please enter a number between %d and %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    /**
     * Reads a positive long amount from the console.
     * Returns 0 and prints an error message on invalid input.
     */
    private static long readAmount(String prompt) {
        System.out.print(prompt);
        String line = SCANNER.nextLine().trim();
        try {
            long val = Long.parseLong(line);
            if (val <= 0) {
                System.out.println("Amount must be a positive number.");
                return 0;
            }
            return val;
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount. Please enter a whole number.");
            return 0;
        }
    }
}
