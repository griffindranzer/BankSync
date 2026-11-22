package concurrency;

import banking.BankingService;
import model.BankAccount;
import model.Transaction;

import java.util.concurrent.Callable;

/**
 * TransactionWorker is a Callable that performs one banking operation
 * and returns the resulting Transaction record.
 *
 * Using Callable (instead of Runnable) allows callers to retrieve the result
 * from the Future returned by ExecutorService.submit().
 *
 * Supported operations:
 *   DEPOSIT, WITHDRAW, BALANCE_ENQUIRY, TRANSFER
 */
public class TransactionWorker implements Callable<Transaction> {

    public enum Operation { DEPOSIT, WITHDRAW, BALANCE_ENQUIRY, TRANSFER }

    private final BankingService service;
    private final Operation      operation;
    private final BankAccount    sourceAccount;
    private final BankAccount    destinationAccount;  // null unless TRANSFER
    private final long           amount;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** For DEPOSIT, WITHDRAW, BALANCE_ENQUIRY. */
    public TransactionWorker(BankingService service, Operation operation,
                             BankAccount account, long amount) {
        this(service, operation, account, null, amount);
    }

    /** For TRANSFER. */
    public TransactionWorker(BankingService service, Operation operation,
                             BankAccount sourceAccount, BankAccount destinationAccount,
                             long amount) {
        this.service            = service;
        this.operation          = operation;
        this.sourceAccount      = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount             = amount;
    }

    // -----------------------------------------------------------------------
    // Callable implementation
    // -----------------------------------------------------------------------

    @Override
    public Transaction call() {
        return switch (operation) {
            case DEPOSIT         -> service.deposit(sourceAccount, amount);
            case WITHDRAW        -> service.withdraw(sourceAccount, amount);
            case BALANCE_ENQUIRY -> service.balanceEnquiry(sourceAccount);
            case TRANSFER        -> {
                if (destinationAccount == null) {
                    throw new IllegalStateException(
                            "Destination account required for TRANSFER.");
                }
                yield service.transfer(sourceAccount, destinationAccount, amount);
            }
        };
    }
}
