package es.codeurjc.service;

import es.codeurjc.model.Account;
import es.codeurjc.model.User;
import es.codeurjc.model.Notification;
import es.codeurjc.model.Transaction;
import es.codeurjc.repository.AccountRepository;
import es.codeurjc.repository.TransactionRepository;
import es.codeurjc.service.notifications.EmailNotificationService;
import es.codeurjc.service.notifications.SmsNotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing bank accounts.
 */
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EmailNotificationService emailService;
    private final SmsNotificationService smsService;
    private final RandomService randomService;

    public AccountService(AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            EmailNotificationService emailService,
            SmsNotificationService smsService,
            RandomService randomService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.randomService = randomService;
    }

    /**
     * Create a new account
     */
    public Account createAccount(User user, Account.AccountType accountType) {
        String accountNumber = generateAccountNumber();
        Account account = new Account(accountNumber, accountType, 0);
        account.setUser(user);
        return accountRepository.save(account);
    }

    /**
     * Generate account number
     */
    private String generateAccountNumber() {
        return String.format("ES%010d", randomService.nextInt(1000000000));
    }

    /**
     * Get account by account number
     */
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Get all accounts for a user
     */
    public List<Account> getUserAccounts(User user) {
        return accountRepository.findByUser(user);
    }

    /*
     * Check if the amount is valid [Deposit/Withdrawal/Transfer]
     */
    private void checkAmount(double amount, double minLimit, double maxLimit, String exMin, String exMax) {
         if (amount <= minLimit) {
            throw new IllegalArgumentException(exMin);
        }
        if (amount > maxLimit) {
            throw new IllegalArgumentException(exMax);
        }
    }

    /*
     * Check sufficient funds [Withdrawal/Transfer]
     */
    private void checkSufficientFunds(Account account, double amount) {
        if (account.getBalance() < amount) {
            throw new IllegalArgumentException("Insufficient funds");
        }
    }

    /*
     * Save transaction [Deposit/Withdrawal/Transfer]
     */
    private void saveTransaction(Account account, Transaction.TransactionType type, double amount, String description) {
        Transaction transaction = new Transaction(account, type, amount, description);
        transactionRepository.save(transaction);
    }

    /*
     * Notify user of successful [Deposit/Withdrawal/Transfer]
     */
    private void sendNotification(Account account, Notification.NotificationType notifType, String emailSubject,
            String emailMessage, String smsSubject, String smsMessage) {

        User.NotificationType type = account.getUser().getNotificationType();

        if (type == User.NotificationType.EMAIL) {
            emailService.sendNotification(account.getUser(), notifType, emailSubject, emailMessage);

        } else if (type == User.NotificationType.SMS) {
            smsService.sendNotification(account.getUser(), notifType, smsSubject, smsMessage);
        }
    }

    /**
     * Deposit money into account
     */
    @Transactional
    public Account deposit(String accountNumber, double amount, String description) {
        checkAmount(amount , 0 , 10000 , "Amount must be positive" , "Amount exceeds maximum deposit limit");

        Account account = getAccount(accountNumber);

        if(account.getUser().isBanned()){
            throw new IllegalStateException("The user is banned and cannot do any operations");
        }


        account.deposit(amount);

        // Save transaction
        saveTransaction(account , Transaction.TransactionType.DEPOSIT, amount , description);

        Account savedAccount = accountRepository.save(account);

        // Send notification
        String subject = "Deposit Confirmation";

        sendNotification(account, Notification.NotificationType.DEPOSIT,
                subject,
                String.format("Deposit of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()),
                subject,
                String.format("Deposit: %.2f EUR. Balance: %.2f EUR", amount, account.getBalance()));

        return savedAccount;
    }

    /*
     * Quick deposit without description
     */
    @Transactional
    public Account deposit(String accountNumber, double amount) {
        return deposit(accountNumber, amount, "Quick deposit");
    }

    /**
     * Withdraw money from account
     */
    @Transactional
    public Account withdraw(String accountNumber, double amount, String description) {
        checkAmount(amount, 0, 5000, "Amount must be positive", "Amount exceeds maximum withdrawal limit");

        Account account = getAccount(accountNumber);

        if(account.getUser().isBanned()){
            throw new IllegalStateException("The user is banned and cannot do any operations");
        }

        checkSufficientFunds(account, amount);

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        double withdrawnLast24h = transactionRepository.sumWithdrawalsAfter(account, since);
        if (withdrawnLast24h + amount >= 5000) {
            throw new IllegalArgumentException("Daily withdrawal limit exceeded");
        }

        checkSufficientFunds(account, amount);
        account.withdraw(amount);

        saveTransaction(account, Transaction.TransactionType.WITHDRAWAL, amount, description);
        Account savedAccount = accountRepository.save(account);

        sendNotification(savedAccount, Notification.NotificationType.WITHDRAWAL, "Withdrawal Confirmation",
                String.format("Withdrawal of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()),
                "Withdrawal",
                String.format("Withdrawal of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()));

        return savedAccount;
    }

    /**
     * Transfer money between accounts
     */
    @Transactional
    public void transfer(String fromAccountNumber, String toAccountNumber, double amount) {
        checkAmount(amount, 0, 20000, "Amount must be positive", "Amount exceeds maximum transfer limit");

        Account sourceAccount = getAccount(fromAccountNumber);
        Account destinationAccount = getAccount(toAccountNumber);

        if(sourceAccount.getUser().isBanned()){
            throw new IllegalStateException("The sender account user is banned and cannot do any operations");
        }
        if(destinationAccount.getUser().isBanned()){
            throw new IllegalStateException("The recipient account user is banned and cannot recieve transfers");
        }


        if (sourceAccount.getAccountNumber().equals(destinationAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to same account");
        }
        checkSufficientFunds(sourceAccount, amount);

        sourceAccount.withdraw(amount);
        destinationAccount.deposit(amount);

        saveTransferTransactions(sourceAccount, destinationAccount, fromAccountNumber, toAccountNumber, amount);

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        sendNotification(sourceAccount, Notification.NotificationType.TRANSFER, "Transfer Sent",
                String.format("Transfer of %.2f EUR to %s. New balance: %.2f EUR", amount, toAccountNumber, sourceAccount.getBalance()),
                "Transfer Sent",
                String.format("Transfer of %.2f EUR to %s. New balance: %.2f EUR", amount, toAccountNumber, sourceAccount.getBalance()));

        sendNotification(destinationAccount, Notification.NotificationType.TRANSFER, "Transfer Received",
                String.format("Transfer of %.2f EUR from %s. New balance: %.2f EUR", amount, fromAccountNumber, destinationAccount.getBalance()),
                "Transfer Received",
                String.format("Transfer of %.2f EUR from %s. New balance: %.2f EUR", amount, fromAccountNumber, destinationAccount.getBalance()));
    }

    /*
     * Save transfer transactions
     */
    private void saveTransferTransactions(Account sourceAccount, Account destinationAccount,
                        String fromAccountNumber, String toAccountNumber, double amount) {
        Transaction sentTransaction = new Transaction(sourceAccount,
                Transaction.TransactionType.TRANSFER_SENT, amount, "Transfer to " + toAccountNumber);
        sentTransaction.setDestinationAccountNumber(toAccountNumber);
        transactionRepository.save(sentTransaction);

        Transaction receivedTransaction = new Transaction(destinationAccount,
                Transaction.TransactionType.TRANSFER_RECEIVED, amount, "Transfer from " + fromAccountNumber);
        receivedTransaction.setDestinationAccountNumber(fromAccountNumber);
        transactionRepository.save(receivedTransaction);
    }

    /**
     * Delete account
     */
    public void deleteAccount(String accountNumber) {
        Account account = getAccount(accountNumber);

        if (account.getBalance() != 0) {
            throw new IllegalArgumentException("Cannot delete account with non-zero balance");
        }

        accountRepository.delete(account);
    }

    /**
     * Get account balance
     */
    public double getBalance(String accountNumber) {
        Account account = getAccount(accountNumber);
        return account.getBalance();
    }

    /**
     * Get account transactions
     */
    public List<Transaction> getTransactions(String accountNumber) {
        Account account = getAccount(accountNumber);
        return transactionRepository.findByAccountOrderByTimestampDesc(account);
    }
}
