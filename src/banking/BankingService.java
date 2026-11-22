package banking;

import model.BankAccount;
import model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BankingService centralises all banking operations.
 *
 * Key design decisions:
 *
 *  1. DEPOSIT / WITHDRAWAL / BALANCE ENQUIRY
 *     Delegate to BankAccount's ReentrantLock-protected methods (deposit,
 *     withdraw, getBalance).  Every balance-touching operation uses the same
 *     single lock — there is no mixed synchronized/ReentrantLock access.
 *
 *  2. FUND TRANSFER (atomic, deadlock-free)
 *     A transfer must deduct from the source AND credit the destination as one
 *     indivisible unit.  We acquire BOTH account locks simultaneously using a
 *     consistent ordering rule:
 *
 *       Always lock the account with the LOWER account number first.
 *
 *     Because every thread obeys the same ordering, circular wait cannot occur
 *     (Thread A waiting for account 1002 while holding 1001 cannot block Thread B
 *     which must also acquire 1001 before 1002).  This is the standard
 *     "resource ordering" deadlock-prevention strategy.
 *
 *  3. TRANSACTION LOG
 *     Every completed operation is recorded in a thread-safe list so callers
 *     can inspect the history.
 */
public class BankingService {

    private final List<Transaction> transactionLog = new CopyOnWriteArrayList<>();

    // -----------------------------------------------------------------------
    // Deposit
    // -----------------------------------------------------------------------

    /**
     * Deposits {@code amount} rupees into {@code account}.
     *
     * @return the completed Transaction record
     */
    public Transaction deposit(BankAccount account, long amount) {
        Transaction txn = Transaction.deposit(account.getAccountNumber(), amount);
        try {
            account.deposit(amount);                      // ReentrantLock inside BankAccount
            txn.setStatus(Transaction.Status.SUCCESS);
            txn.setNotes(String.format("New balance: Rs. %,d", account.getBalance()));
        } catch (IllegalArgumentException e) {
            txn.setStatus(Transaction.Status.FAILED);
            txn.setNotes("Invalid amount: " + e.getMessage());
        }
        transactionLog.add(txn);
        return txn;
    }

    // -----------------------------------------------------------------------
    // Withdrawal
    // -----------------------------------------------------------------------

    /**
     * Withdraws {@code amount} rupees from {@code account}.
     *
     * @return the completed Transaction record
     */
    public Transaction withdraw(BankAccount account, long amount) {
        Transaction txn = Transaction.withdrawal(account.getAccountNumber(), amount);
        try {
            boolean ok = account.withdraw(amount);        // ReentrantLock inside BankAccount
            if (ok) {
                txn.setStatus(Transaction.Status.SUCCESS);
                txn.setNotes(String.format("New balance: Rs. %,d", account.getBalance()));
            } else {
                txn.setStatus(Transaction.Status.FAILED);
                txn.setNotes(String.format("Insufficient funds. Balance: Rs. %,d, Required: Rs. %,d",
                        account.getBalance(), amount));
            }
        } catch (IllegalArgumentException e) {
            txn.setStatus(Transaction.Status.FAILED);
            txn.setNotes("Invalid amount: " + e.getMessage());
        }
        transactionLog.add(txn);
        return txn;
    }

    // -----------------------------------------------------------------------
    // Balance Enquiry
    // -----------------------------------------------------------------------

    /**
     * Returns the current balance without modifying the account.
     *
     * @return the completed Transaction record
     */
    public Transaction balanceEnquiry(BankAccount account) {
        Transaction txn = Transaction.balanceEnquiry(account.getAccountNumber());
        long balance = account.getBalance();              // ReentrantLock inside BankAccount
        txn.setStatus(Transaction.Status.SUCCESS);
        txn.setNotes(String.format("Balance: Rs. %,d", balance));
        transactionLog.add(txn);
        return txn;
    }

    // -----------------------------------------------------------------------
    // Fund Transfer – atomic, deadlock-free
    // -----------------------------------------------------------------------

    /**
     * Atomically transfers {@code amount} rupees from {@code source} to {@code destination}.
     *
     * <p><b>Atomic guarantee:</b> either both debit and credit succeed, or neither
     * account is modified.  We use try/finally inside the locked region so that
     * an unexpected exception cannot leave the source debited but the destination
     * uncredited.
     *
     * <p><b>Deadlock prevention:</b> we always lock the account with the smaller
     * account number first.  This imposes a global ordering on lock acquisition
     * and breaks circular wait regardless of which direction the transfer flows.
     *
     * @return the completed Transaction record
     */
    public Transaction transfer(BankAccount source, BankAccount destination, long amount) {
        Transaction txn = Transaction.transfer(
                source.getAccountNumber(), destination.getAccountNumber(), amount);

        if (source.getAccountNumber() == destination.getAccountNumber()) {
            txn.setStatus(Transaction.Status.FAILED);
            txn.setNotes("Cannot transfer to the same account.");
            transactionLog.add(txn);
            return txn;
        }

        // --- Determine lock order (lower account number first) ---------------
        // This is the DEADLOCK PREVENTION rule: consistent resource ordering.
        BankAccount first  = (source.getAccountNumber() < destination.getAccountNumber())
                ? source : destination;
        BankAccount second = (first == source) ? destination : source;

        ReentrantLock lockFirst  = first.getLock();
        ReentrantLock lockSecond = second.getLock();

        lockFirst.lock();
        try {
            lockSecond.lock();
            try {
                // Both locks are held – now perform the atomic transfer.
                // withdrawUnderLock / depositUnderLock skip their own lock
                // acquisition; the caller (this method) already holds both
                // accounts' ReentrantLocks, so the two-account update is atomic.
                boolean deducted = source.withdrawUnderLock(amount);
                if (deducted) {
                    destination.depositUnderLock(amount);
                    txn.setStatus(Transaction.Status.SUCCESS);
                    txn.setNotes(String.format(
                            "Transferred Rs. %,d from #%d (bal Rs. %,d) to #%d (bal Rs. %,d)",
                            amount,
                            source.getAccountNumber(), source.getBalance(),
                            destination.getAccountNumber(), destination.getBalance()));
                } else {
                    // Source had insufficient funds – destination is NEVER touched.
                    txn.setStatus(Transaction.Status.FAILED);
                    txn.setNotes(String.format(
                            "Insufficient funds in #%d. Balance: Rs. %,d, Required: Rs. %,d",
                            source.getAccountNumber(), source.getBalance(), amount));
                }
            } finally {
                lockSecond.unlock();
            }
        } catch (IllegalArgumentException e) {
            txn.setStatus(Transaction.Status.FAILED);
            txn.setNotes("Invalid amount: " + e.getMessage());
        } finally {
            lockFirst.unlock();
        }

        transactionLog.add(txn);
        return txn;
    }

    // -----------------------------------------------------------------------
    // Transaction history
    // -----------------------------------------------------------------------

    /** Returns an unmodifiable view of the transaction log. */
    public List<Transaction> getTransactionLog() {
        return Collections.unmodifiableList(transactionLog);
    }

    /** Clears the transaction log (used between test cases). */
    public void clearLog() {
        transactionLog.clear();
    }
}
