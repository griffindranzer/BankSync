package model;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BankAccount represents an individual customer bank account.
 *
 * LOCKING STRATEGY (single, consistent):
 *   Every operation that reads or writes the balance — deposit, withdraw,
 *   getBalance, and the transfer helpers (depositUnderLock, withdrawUnderLock)
 *   — uses the SAME private ReentrantLock.
 *
 *   This eliminates the dual-lock hazard that existed when deposit/withdraw/
 *   getBalance used the intrinsic synchronized monitor while BankingService
 *   used the ReentrantLock for transfers.  With two different lock objects
 *   guarding the same field, volatile was required to patch the memory-
 *   visibility gap.  With a single ReentrantLock throughout, volatile is
 *   unnecessary and has been removed.
 *
 * METHOD CATEGORIES:
 *   Safe (acquire lock internally):
 *     deposit(), withdraw(), getBalance()
 *     — Called by BankingService for single-account operations.
 *
 *   Lock-delegated (caller must already hold this.lock):
 *     depositUnderLock(), withdrawUnderLock()
 *     — Called by BankingService.transfer() while holding both account locks.
 *     — Never acquire the lock themselves; doing so would cause re-entrancy
 *       confusion and obscure the locking contract in BankingService.
 *
 *   Unsafe (no synchronisation at all):
 *     unsafeGetBalance(), unsafeSetBalance()
 *     — FOR RACE-CONDITION DEMONSTRATION ONLY.
 *     — Must never be called from normal banking operations.
 *
 * Balance is stored as a plain long (not volatile) because every read and
 * write is now protected by the same ReentrantLock, which provides both
 * mutual exclusion AND the happens-before memory-visibility guarantee.
 */
public class BankAccount {

    // -----------------------------------------------------------------------
    // Static ID generator – thread-safe, ensures unique account numbers.
    // -----------------------------------------------------------------------
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000);

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final long          accountNumber;
    private final String        customerName;

    // Plain long — no volatile needed.
    // All safe access goes through 'lock'; the lock's unlock() establishes a
    // happens-before edge that makes the written value visible to the next
    // thread that acquires 'lock'.
    private       long          balance;

    /**
     * The single lock used for ALL balance operations in this account.
     *
     * BankingService.transfer() acquires the locks of BOTH accounts before
     * calling depositUnderLock/withdrawUnderLock.  It always acquires them in
     * ascending account-number order to prevent circular wait (deadlock).
     */
    private final ReentrantLock lock = new ReentrantLock();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public BankAccount(String customerName, long initialBalance) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Customer name must not be blank.");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative.");
        }
        this.accountNumber = ID_GENERATOR.getAndIncrement();
        this.customerName  = customerName;
        this.balance       = initialBalance;
    }

    // -----------------------------------------------------------------------
    // Safe banking operations  (each acquires / releases 'lock' internally)
    // -----------------------------------------------------------------------

    /**
     * Thread-safe deposit.
     *
     * Acquires the account's ReentrantLock, adds the amount, and releases.
     *
     * @param amount amount in rupees (must be > 0)
     * @throws IllegalArgumentException if amount is invalid
     */
    public void deposit(long amount) {
        validateAmount(amount);
        lock.lock();
        try {
            balance += amount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Thread-safe withdrawal.
     *
     * Acquires the account's ReentrantLock, checks funds, deducts if possible,
     * and releases.  The check-then-act sequence is indivisible under the lock.
     *
     * @param amount amount in rupees (must be > 0)
     * @return true if the withdrawal succeeded; false if funds are insufficient
     * @throws IllegalArgumentException if amount is invalid
     */
    public boolean withdraw(long amount) {
        validateAmount(amount);
        lock.lock();
        try {
            if (balance < amount) {
                return false;   // insufficient funds — balance untouched
            }
            balance -= amount;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Thread-safe balance enquiry.
     *
     * Acquires the account's ReentrantLock to guarantee a consistent read.
     *
     * @return current balance in rupees
     */
    public long getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Lock access — for multi-account atomic operations in BankingService
    // -----------------------------------------------------------------------

    /**
     * Returns this account's ReentrantLock.
     *
     * BankingService.transfer() uses this to acquire both account locks in a
     * consistent order before calling depositUnderLock / withdrawUnderLock.
     */
    public ReentrantLock getLock() {
        return lock;
    }

    /**
     * Credits the balance WITHOUT acquiring 'lock'.
     *
     * <p><b>Precondition:</b> the caller (BankingService.transfer) MUST already
     * hold this account's ReentrantLock.  Calling this method without holding the
     * lock is a programming error and will produce data races.
     *
     * <p>This method exists so that transfer can perform both the debit and credit
     * inside a single already-established locked region, ensuring the two-account
     * operation is atomic.
     */
    public void depositUnderLock(long amount) {
        validateAmount(amount);
        balance += amount;
    }

    /**
     * Debits the balance WITHOUT acquiring 'lock'.
     *
     * <p><b>Precondition:</b> the caller (BankingService.transfer) MUST already
     * hold this account's ReentrantLock.
     *
     * @return true if the debit succeeded; false if funds are insufficient
     */
    public boolean withdrawUnderLock(long amount) {
        validateAmount(amount);
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    // -----------------------------------------------------------------------
    // UNSAFE operations — FOR RACE-CONDITION DEMONSTRATION ONLY
    //
    // These methods deliberately bypass the ReentrantLock.
    // They exist solely to demonstrate what happens when shared mutable state
    // is accessed without synchronisation.
    //
    // NEVER call these from normal banking operations.
    // -----------------------------------------------------------------------

    /**
     * <b>UNSAFE — NO LOCK ACQUIRED.</b>
     * Returns the current balance without locking.
     * May return a stale or mid-update value visible to this thread's CPU core.
     */
    public long unsafeGetBalance() {
        return balance;
    }

    /**
     * <b>UNSAFE — NO LOCK ACQUIRED.</b>
     * Directly overwrites the balance without locking.
     * Used by race-condition demonstrations to simulate the write phase of a
     * lost-update scenario.
     */
    public void unsafeSetBalance(long newBalance) {
        balance = newBalance;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public long   getAccountNumber() { return accountNumber; }
    public String getCustomerName()  { return customerName;  }

    @Override
    public String toString() {
        return String.format("Account[#%d | %s | Rs. %,d]",
                accountNumber, customerName, getBalance());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------
    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be positive. Received: " + amount);
        }
    }
}
