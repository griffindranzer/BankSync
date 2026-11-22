package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transaction represents a single banking operation.
 *
 * Every transaction has:
 *   - a unique sequential ID
 *   - a type (DEPOSIT, WITHDRAWAL, BALANCE_ENQUIRY, TRANSFER)
 *   - a status (PENDING, SUCCESS, FAILED)
 *   - optional source / destination account numbers (for transfers)
 *   - the amount involved
 *   - a timestamp and the name of the executing thread
 *   - a human-readable notes string capturing the outcome
 */
public class Transaction {

    // -----------------------------------------------------------------------
    // Enumerations
    // -----------------------------------------------------------------------

    public enum Type {
        DEPOSIT,
        WITHDRAWAL,
        BALANCE_ENQUIRY,
        TRANSFER
    }

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED
    }

    // -----------------------------------------------------------------------
    // Static ID counter
    // -----------------------------------------------------------------------
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final long          transactionId;
    private final Type          type;
    private final long          sourceAccountNumber;        // -1 if not applicable
    private final long          destinationAccountNumber;   // -1 if not applicable
    private final long          amount;
    private final String        threadName;
    private final LocalDateTime timestamp;
    private       Status        status;
    private       String        notes;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public Transaction(Type type, long sourceAccountNumber, long destinationAccountNumber,
                       long amount) {
        this.transactionId          = ID_COUNTER.getAndIncrement();
        this.type                   = type;
        this.sourceAccountNumber    = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.amount                 = amount;
        this.threadName             = Thread.currentThread().getName();
        this.timestamp              = LocalDateTime.now();
        this.status                 = Status.PENDING;
        this.notes                  = "";
    }

    // -----------------------------------------------------------------------
    // Convenience factories
    // -----------------------------------------------------------------------

    public static Transaction deposit(long accountNumber, long amount) {
        return new Transaction(Type.DEPOSIT, accountNumber, -1, amount);
    }

    public static Transaction withdrawal(long accountNumber, long amount) {
        return new Transaction(Type.WITHDRAWAL, accountNumber, -1, amount);
    }

    public static Transaction balanceEnquiry(long accountNumber) {
        return new Transaction(Type.BALANCE_ENQUIRY, accountNumber, -1, 0);
    }

    public static Transaction transfer(long from, long to, long amount) {
        return new Transaction(Type.TRANSFER, from, to, amount);
    }

    // -----------------------------------------------------------------------
    // Mutators
    // -----------------------------------------------------------------------

    public void setStatus(Status status) { this.status = status; }
    public void setNotes(String notes)   { this.notes  = notes;  }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public long          getTransactionId()           { return transactionId;          }
    public Type          getType()                    { return type;                   }
    public long          getSourceAccountNumber()     { return sourceAccountNumber;    }
    public long          getDestinationAccountNumber(){ return destinationAccountNumber;}
    public long          getAmount()                  { return amount;                 }
    public String        getThreadName()              { return threadName;             }
    public LocalDateTime getTimestamp()               { return timestamp;              }
    public Status        getStatus()                  { return status;                 }
    public String        getNotes()                   { return notes;                  }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------
    @Override
    public String toString() {
        String accountInfo;
        if (type == Type.TRANSFER) {
            accountInfo = String.format("Account #%d -> Account #%d",
                    sourceAccountNumber, destinationAccountNumber);
        } else {
            accountInfo = "Account #" + sourceAccountNumber;
        }

        return String.format("TXN[%d] %-16s | %-20s | Rs. %,8d | %-7s | Thread: %-18s | %s | %s",
                transactionId,
                type,
                accountInfo,
                amount,
                status,
                threadName,
                timestamp.format(FORMATTER),
                notes);
    }
}
