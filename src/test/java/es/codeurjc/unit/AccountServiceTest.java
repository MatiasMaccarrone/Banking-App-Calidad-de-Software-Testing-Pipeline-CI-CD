package es.codeurjc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import es.codeurjc.model.Account;
import es.codeurjc.model.Account.AccountType;
import es.codeurjc.model.Notification;
import es.codeurjc.model.Notification.NotificationType;
import es.codeurjc.model.Transaction.TransactionType;
import es.codeurjc.model.User;
import es.codeurjc.repository.AccountRepository;
import es.codeurjc.repository.TransactionRepository;
import es.codeurjc.service.AccountService;
import es.codeurjc.service.RandomService;
import es.codeurjc.service.notifications.EmailNotificationService;
import es.codeurjc.service.notifications.SmsNotificationService;
import es.codeurjc.model.Transaction;

/* UNITARY TESTS 
- createAcount(): Alejandro
	1. verify that the account number is generated correctly.
	2. verify that the account is correctly saved in the repository (repository.save()).

- rm(): Alejandro
	3. throw exception when balance <= 0.
	4. verify that the account is removed from the repository.

- deposit(): Carla
	5. throw exception when values <= 0. 
    6. throw exception when values > limit.
	7. verify if the amount is correctly incremented in the account. 
	8. verify if the email is sent.
	9. verify if the sms is sent.

- withdraw(): Matías
	10. throw exception when values <= 0. 
	11. throw exception when values > limit.
	12. throw exception when account has insufficient funds.
	13. verify if the amount is correctly decreased in the account. 
	14. verify if the email is sent.
	15. verify if the sms is sent.

- transfer(): Raul
	16. throw exception when values <= 0.
	17. throw exception when values > limit.
    18. throw exception when trying to transfer money to the same account
	19. throw exception when account has insufficient funds.
	20. verify if the amount is correctly decreased in the account.

	Carla
	21.  verify if the amount is correctly incremented in the account. 
	
	Alejandro
	22. verify if the email is sent.
	23. verify if the sms is sent.

- quickDeposit(): Raúl
	24. verify quickDeposit is calling deposit with the correct parameters.

- getUserAccounts(): Matias
	25. verify if the accounts of a user are correctly returned.

- getTransactions(): Alejandro
	26. verify if the transactions are returned in descending order.
*/

/* BRANCHES NOT REACHED BY THE TESTS
	- Issue 5 is not covered by the tests; the branch `if (amount > 50000)` is never reached. This will be resolved with refactoring.
	- `QuickDeposit` is not tested at first because it has the same functionality as `Deposit`, we will see if it's necessary to test it after refactoring.
	- getUserAccounts and getTransactions are not tested at first because they are simple queries to the repository, we will see if it's necessary to test them after refactoring.
*/

/* UNITARY TEST USER BANNED
- deposit():
	1. throws exception if user is banned. 

- withdraw():
	2. throws exception if user is banned. 

- transfer():
	3. throws exception if receiver is banned.
	4. throws exception if emisor is banned.
*/

/* UNITARY TEST CUMULATIVE WITHDRAWAL
	1. Allows withdrawal when cumulative 24h amount stays below 5000
  2. When 24h limit is exceeded, no transaction is saved and no notification is sent
  3. Blocks withdrawal when cumulative 24h amount reaches or exceeds 5000
*/

public class AccountServiceTest {
	private AccountService accountService;

	// Declaration of mocks
	private AccountRepository accountRepository;
	private TransactionRepository transactionRepository;
	private EmailNotificationService emailNotificationService;
	private SmsNotificationService smsNotificationService;
	private RandomService randomService;

	// Generating accounts for testing with different balances and different
	// ammounts to make the transactions
	public static Stream<Arguments> generateAccountsGreaterBalance() {
		return Stream.of(
				Arguments.of(generateTestAccounts("ES2039452350", 500), 1),
				Arguments.of(generateTestAccounts("ES0000000001", 1500.0), 1000.0),
				Arguments.of(generateTestAccounts("ES9328457203", 4367.0), 2040.0));
	}

	// Generating accounts for testing with different balances and different
	// ammounts to make the transactions
	public static Stream<Arguments> generateAccountsGreaterAmount() {
		return Stream.of(
				Arguments.of(generateTestAccounts("ES2039452350", 0), 500),
				Arguments.of(generateTestAccounts("ES0000000001", 1000.0), 1500.0),
				Arguments.of(generateTestAccounts("ES9328457203", 2040.0), 4367.0));
	}

	// Functions to create ammounts 
	public static Stream<Arguments> cumulative24hAllowedScenarios() {
		return Stream.of(
				Arguments.of(0.0, 1000.0), // nothing withdrawn yet
				Arguments.of(2000.0, 1000.0), // 2000 + 1000 = 3000 < 5000
				Arguments.of(3999.0, 1000.0) // 3999 + 1000 = 4999 < 5000 (just below boundary)
		);
	}
	public static Stream<Arguments> cumulative24hBlockedScenarios() {
		return Stream.of(
				Arguments.of(4000.0, 1000.0), // 4000 + 1000 = 5000 (exact boundary, blocked)
				Arguments.of(4500.0, 600.0) // 4500 + 600 = 5100 > 5000
		);
	}

	// Function to create the accounts
	public static Account generateTestAccounts(String accountNumber, double balance) {
		Account account = new Account(accountNumber, AccountType.CHECKING, balance);
		account.setUser(new User("TestName", "password"));
		return account;
	}

	// Function to create banned accounts
	public static Account generateBannedAccount(String accountNumber, double balance) {
		Account account = new Account(accountNumber, AccountType.CHECKING, balance);
		User user = new User("BannedUser", "password");
		user.setBanned(true);
		account.setUser(user);
		return account;
	}

	// Generating a valid accountService with mocks before each test
	@BeforeEach
	public void accountServiceGeneration() {
		accountRepository = mock(AccountRepository.class);
		transactionRepository = mock(TransactionRepository.class);
		emailNotificationService = mock(EmailNotificationService.class);
		smsNotificationService = mock(SmsNotificationService.class);
		randomService = mock(RandomService.class);

		this.accountService = new AccountService(accountRepository, transactionRepository, emailNotificationService,
				smsNotificationService, randomService);
	}

	// CREATE ACCOUNT TEST
	// Test 1. verify that the account number is generated correctly.
	@Test
	public void createAccountGenerateCorrectAccountNumberTest() {
		// Given
		User user = new User("TestName", "password");
		int randomNumber = 111111111;
		String expectedAccountNumber = String.format("ES%010d", randomNumber);
		Account expectedAccount = new Account(expectedAccountNumber, Account.AccountType.CHECKING, 0);
		expectedAccount.setUser(user);

		when(randomService.nextInt(1000000000)).thenReturn(randomNumber);
		when(accountRepository.save(any(Account.class))).thenReturn(expectedAccount);

		// When
		Account createdAccount = accountService.createAccount(user, Account.AccountType.CHECKING);
		String accountNumber = createdAccount.getAccountNumber();

		// Then
		assertTrue(accountNumber.startsWith("ES"));
		assertEquals(12, accountNumber.length());
		assertTrue(accountNumber.substring(2).matches("\\d{10}"));
		assertEquals(expectedAccountNumber, accountNumber);
	}

	// Test 2. verify that the account is correctly saved in the repository
	// (repository.save()).
	@Test
	public void createAccountSavesAccountsInRepositoryTest() {
		// Given
		User user = new User("TestName", "password");
		int randomNumber = 111111111;
		String expectedAccountNumber = String.format("ES%010d", randomNumber);

		when(randomService.nextInt(1000000000)).thenReturn(randomNumber);

		// When
		accountService.createAccount(user, Account.AccountType.CHECKING);

		// Then
		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).save(captor.capture());

		Account savedAccount = captor.getValue();
		assertEquals(expectedAccountNumber, savedAccount.getAccountNumber());
		assertEquals(Account.AccountType.CHECKING, savedAccount.getAccountType());
		assertEquals(0, savedAccount.getBalance());
		assertEquals(user, savedAccount.getUser());
	}

	// REMOVE ACCOUNT TEST
	// Test 3. throw exception when balance <= 0.
	@ParameterizedTest
	@ValueSource(doubles = { 1.0, -1.0, -500.0 })
	public void deleteAccountThrowsExceptionWhenBalanceIsNotZeroTest(double balance) {
		Account account = generateTestAccounts("ES1111111111", balance);
		when(accountRepository.findByAccountNumber("ES1111111111")).thenReturn(Optional.of(account));

		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.deleteAccount("ES1111111111");
		});

		assertEquals("Cannot delete account with non-zero balance", e.getMessage());

	}

	// Test 4. verify that the account is removed from the repository.
	@Test
	public void deleteAccountDeletesAccountFromRepositoryTest() {
		// Given
		Account account = generateTestAccounts("ES1111111111", 0);
		when(accountRepository.findByAccountNumber("ES1111111111")).thenReturn(Optional.of(account));

		// When
		accountService.deleteAccount("ES1111111111");

		// Then
		verify(accountRepository).delete(account);
	}

	// DEPOSIT TESTS
	// Test 5. throw exception when values <= 0.
	@ParameterizedTest
	@ValueSource(doubles = { 0.0, -5.0, -500.50 })
	public void depositThrowsExceptionWhenAmountIsZeroOrNegativeTest(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.deposit("ES4561239870", amount, "Deposit tests");
		});

		assertEquals("Amount must be positive", e.getMessage());
	}

	// Test 6. throw exception when values > limit.
	@ParameterizedTest
	@ValueSource(doubles = { 10001, 50000 })
	public void depositThrowsExceptionWhenAmountIsGreaterThanLimit(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.deposit("ES4561239870", amount, "Deposit tests");
		});

		assertEquals("Amount exceeds maximum deposit limit", e.getMessage());
	}

	// Test 7. verify if the amount is correctly incremented in the account.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void depositIncrementsAmountCorrectlyInAccountTest(Account account, double amount) {
		// Given
		String accountNumber = account.getAccountNumber();
		double expectedBalance = account.getBalance() + amount;
		when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account)); // Tells mockito to
																										// pick the test
																										// account
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.deposit(accountNumber, amount, "Deposit test");

		// Then
		assertEquals(expectedBalance, account.getBalance());
	}

	// Test 8. verify if the email is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void depositSendsNotificationEmailAndNotSmsTest(Account account, double amount) {
		// Given
		String accountNumber = account.getAccountNumber();
		account.getUser().setNotificationType(User.NotificationType.EMAIL);
		when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.deposit(accountNumber, amount, "Deposit test");

		// Then
		verify(emailNotificationService, times(1)).sendNotification(
				account.getUser(),
				NotificationType.DEPOSIT,
				"Deposit Confirmation",
				String.format("Deposit of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()));
		verifyNoInteractions(smsNotificationService);
	}

	// Test 9. verify if the sms is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void depositSendsNotificationSmsAndNotEmailTest(Account account, double amount) {
		// Given
		String accountNumber = account.getAccountNumber();
		account.getUser().setNotificationType(User.NotificationType.SMS);
		when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.deposit(accountNumber, amount, "Deposit test");

		// Then
		verify(smsNotificationService, times(1)).sendNotification(
				account.getUser(),
				NotificationType.DEPOSIT,
				"Deposit Confirmation",
				String.format("Deposit: %.2f EUR. Balance: %.2f EUR", amount, account.getBalance()));
		verifyNoInteractions(emailNotificationService);
	}

	// WITHDRAW TEST
	// Test 10. Throw exception when values <= 0.
	@ParameterizedTest
	@ValueSource(doubles = { 0, 0.0, -100, -0.1 })
	public void withdrawThrowsExceptionWhenAmountIsZeroOrNegativeTest(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.withdraw("ES1234567890", amount, "withdraw test nº10");
		});

		assertEquals("Amount must be positive", e.getMessage());
	}

	// Test 11. Throw exception when values > limit.
	@ParameterizedTest
	@ValueSource(doubles = { 5000.01, 5001, 10000 })
	public void withdrawThrowsExceptionWhenAmountExceedsLimitTest(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.withdraw("ES1234567890", amount, "withdraw test nº11");
		});

		assertEquals("Amount exceeds maximum withdrawal limit", e.getMessage());
	}

	// Test 12. Throw exception when account has insufficient funds.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterAmount")
	public void withdrawThrowsExceptionWhenInsufficientFundsTest(Account account, double amount) {

		// When
		when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));

		// Then
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.withdraw(account.getAccountNumber(), amount, "withdraw test nº12");
		});
		assertEquals("Insufficient funds", e.getMessage());
	}

	// Test 13. Verify if the amount withdrew is correctly decreased of the account.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void withdrawTheAmountDecreasedIsCorrect(Account account, double ammount) {
		// All accounts can withdrawal money without any problem
		if (ammount == 0) {
			ammount++;
		}

		// When
		when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// Then
		double expectedBalance = accountService.getBalance(account.getAccountNumber()) - ammount;
		assertEquals(expectedBalance,
				accountService.withdraw(account.getAccountNumber(), ammount, "withdraw test nº13").getBalance());
	}

	// Test 14. Verify if the email is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void withdrawSendsNotificationEmailAndNotSmsTest(Account account, double amount) {
		if (account.getBalance() < amount) {
			account.setBalance(amount + 100);
		}
		// Given
		String accountNumber = account.getAccountNumber();
		account.getUser().setNotificationType(User.NotificationType.EMAIL);
		when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.withdraw(accountNumber, amount, "withdraw test nº14");

		// Then
		verify(emailNotificationService, times(1)).sendNotification(
				account.getUser(),
				NotificationType.WITHDRAWAL,
				"Withdrawal Confirmation",
				String.format("Withdrawal of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()));
		verifyNoInteractions(smsNotificationService);
	}

	// Test 15. Verify if the sms is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void withdrawSendsNotificationSmsAndNotEmail(Account account, double amount) {
		if (account.getBalance() < amount) {
			account.setBalance(amount + 100);
		}
		// Given
		String accountNumber = account.getAccountNumber();
		account.getUser().setNotificationType(User.NotificationType.SMS);
		when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.withdraw(accountNumber, amount, "withdraw test nº15");

		// Then
		verify(smsNotificationService, times(1)).sendNotification(
				account.getUser(),
				Notification.NotificationType.WITHDRAWAL,
				"Withdrawal",
				String.format("Withdrawal of %.2f EUR. New balance: %.2f EUR", amount, account.getBalance()));
		verifyNoInteractions(emailNotificationService);
	}

	// TRANSFER TESTS
	// Test 16. throw exception when values <= 0.
	@ParameterizedTest
	@ValueSource(doubles = { 0.0, -1.0, -500.0 })
	public void transferThrowsExceptionWhenAmountIsZeroOrNegativeTest(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.transfer("ES0000000001", "ES0000000002", amount);
		});
		assertEquals("Amount must be positive", e.getMessage());
	}

	// Test 17. throw exception when values > limit.
	@ParameterizedTest
	@ValueSource(doubles = { 20001.0, 50000.0, 999999.0 })
	public void transferThrowsExceptionWhenAmountExceedsLimitTest(double amount) {
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.transfer("ES0000000001", "ES0000000002", amount);
		});
		assertEquals("Amount exceeds maximum transfer limit", e.getMessage());
	}

	// Test 18. throw exception when trying to transfer money to the same account
	@Test
	public void transferThrowsExceptionWhenTransferringToSameAccountTest() {
		Account account = generateTestAccounts("ES0000000001", 1000.0);
		when(accountRepository.findByAccountNumber("ES0000000001"))
				.thenReturn(Optional.of(account))
				.thenReturn(Optional.of(account));

		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.transfer("ES0000000001", "ES0000000001", 100.0);
		});

		assertEquals("Cannot transfer to same account", e.getMessage());
	}

	// Test 19. throw exception when account has insufficient funds.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterAmount")
	public void transferThrowsExceptionWhenInsufficientFundsTest(Account fromAccount, double amount) {
		Account toAccount = generateTestAccounts("ES1111111111", 0.0);
		Account fromAccountEmpty = generateTestAccounts(fromAccount.getAccountNumber(), 0.0);
		String fromAccountNumber = fromAccountEmpty.getAccountNumber();
		String toAccountNumber = toAccount.getAccountNumber();

		when(accountRepository.findByAccountNumber(fromAccountNumber)).thenReturn(Optional.of(fromAccountEmpty));
		when(accountRepository.findByAccountNumber(toAccountNumber)).thenReturn(Optional.of(toAccount));

		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.transfer(fromAccountNumber, toAccountNumber, amount);
		});
		assertEquals("Insufficient funds", e.getMessage());
	}

	// Test 20. verify if the amount is correctly decreased in the account.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void transferCorrectlyDecrementsOriginAccountBalanceTest(Account fromAccount, double amount) {
		// Given
		Account fromAccountWithFunds = generateTestAccounts(fromAccount.getAccountNumber(), 10000.0);
		Account toAccount = generateTestAccounts("ES1111111111", 0.0);
		String fromAccountNumber = fromAccountWithFunds.getAccountNumber();
		String toAccountNumber = toAccount.getAccountNumber();
		double expectedBalance = fromAccountWithFunds.getBalance() - amount;

		when(accountRepository.findByAccountNumber(fromAccountNumber)).thenReturn(Optional.of(fromAccountWithFunds));
		when(accountRepository.findByAccountNumber(toAccountNumber)).thenReturn(Optional.of(toAccount));

		// When
		accountService.transfer(fromAccountNumber, toAccountNumber, amount);

		// Then
		assertEquals(expectedBalance, fromAccountWithFunds.getBalance());
	}

	// Test 21. verify if the amount is correctly incremented in the account.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterAmount")
	public void transferIncrementsAmountCorrectlyInDestinationAccounTest(Account toAccount, double amount) {
		// Given
		Account fromAccount = generateTestAccounts("ES1111111111", 10000.0);
		String fromAccountNumber = fromAccount.getAccountNumber();
		String toAccountNumber = toAccount.getAccountNumber();
		double expectedBalance = toAccount.getBalance() + amount;

		when(accountRepository.findByAccountNumber(fromAccountNumber)).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findByAccountNumber(toAccountNumber)).thenReturn(Optional.of(toAccount));

		// When
		accountService.transfer(fromAccountNumber, toAccountNumber, amount);

		// Then
		assertEquals(expectedBalance, toAccount.getBalance());
	}

	// Test 22. verify if the email is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void transferSendsNotificationEmailAndNotSmsTest(Account fromAccount, double amount) {
		// Given
		Account toAccount = generateTestAccounts("ES1111111111", 500);
		toAccount.getUser().setNotificationType(User.NotificationType.EMAIL);
		String toAccountNumber = toAccount.getAccountNumber();

		String fromAccountNumber = fromAccount.getAccountNumber();
		fromAccount.getUser().setNotificationType(User.NotificationType.EMAIL);

		when(accountRepository.findByAccountNumber(fromAccountNumber)).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findByAccountNumber(toAccountNumber)).thenReturn(Optional.of(toAccount));

		// When
		accountService.transfer(fromAccountNumber, toAccountNumber, amount);

		// Then
		verify(emailNotificationService, times(1)).sendNotification(
				toAccount.getUser(),
				Notification.NotificationType.TRANSFER,
				"Transfer Received",
				String.format("Transfer of %.2f EUR from %s. New balance: %.2f EUR", amount, fromAccountNumber,
						toAccount.getBalance()));
		verifyNoInteractions(smsNotificationService);
	}

	// Test 23. verify if the sms is sent.
	@ParameterizedTest
	@MethodSource("generateAccountsGreaterBalance")
	public void transferSendsNotificationSmsAndNotEmailTest(Account fromAccount, double amount) {
		// Given

		Account toAccount = generateTestAccounts("ES1111111111", 500);
		toAccount.getUser().setNotificationType(User.NotificationType.SMS);
		String toAccountNumber = toAccount.getAccountNumber();

		String fromAccountNumber = fromAccount.getAccountNumber();
		fromAccount.getUser().setNotificationType(User.NotificationType.SMS);

		when(accountRepository.findByAccountNumber(fromAccountNumber)).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findByAccountNumber(toAccountNumber)).thenReturn(Optional.of(toAccount));

		// When
		accountService.transfer(fromAccountNumber, toAccountNumber, amount);

		// Then
		verify(smsNotificationService, times(1)).sendNotification(
				toAccount.getUser(),
				Notification.NotificationType.TRANSFER,
				"Transfer Received",
				String.format("Transfer of %.2f EUR from %s. New balance: %.2f EUR", amount, fromAccountNumber,
						toAccount.getBalance()));
		verifyNoInteractions(emailNotificationService);
	}

	// Test 24. verify quickDeposit is calling deposit with the correct parameters.
	@Test
	public void quickDepositCallsDepositWithCorrectParametersTest() {
		// Given
		Account account = generateTestAccounts("ES0000000001", 500.0);
		when(accountRepository.findByAccountNumber("ES0000000001")).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);

		// When
		accountService.deposit("ES0000000001", 100.0);

		// Then
		ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionRepository).save(captor.capture());

		Transaction savedTransaction = captor.getValue();
		assertEquals("Quick deposit", savedTransaction.getDescription());
		assertEquals(100.0, savedTransaction.getAmount());
	}

	// Test 25. Verify that getUserAccounts returns the correct accounts from the
	// repository
	@Test
	public void getUserAccountsReturnsCorrectAccountsTest() {
		// Given
		User user = new User("TestUser", "password");
		List<Account> expectedAccounts = new ArrayList<>();
		expectedAccounts.add(new Account("ES0000000001", AccountType.CHECKING, 100.0));
		expectedAccounts.add(new Account("ES0000000002", AccountType.SAVINGS, 200.0));

		// When
		when(accountRepository.findByUser(user)).thenReturn(expectedAccounts);
		List<Account> result = accountService.getUserAccounts(user);

		// Then
		assertEquals(2, result.size());
		assertTrue(result.containsAll(expectedAccounts));
		verify(accountRepository, times(1)).findByUser(user);
	}

	// Test 26. verify if the transactions are returned in descending order.
	@Test
	public void getTransactionsReturnInDescendingOrderTest() {
		// Given
		Account account = generateTestAccounts("ES1111111111", 10000.0);
		Transaction t1 = new Transaction(account, TransactionType.TRANSFER_SENT, 1000.0, "Transaction sent");
		Transaction t2 = new Transaction(account, TransactionType.TRANSFER_RECEIVED, 5000.0, "Transaction recieved");
		Transaction t3 = new Transaction(account, TransactionType.TRANSFER_SENT, 2000.0, "Transaction sent");

		t1.setTimestamp(LocalDateTime.of(2026, 5, 4, 17, 35));
		t2.setTimestamp(LocalDateTime.of(2026, 4, 3, 14, 22));
		t3.setTimestamp(LocalDateTime.of(2026, 3, 2, 15, 41));

		List<Transaction> expectedTransactions = new ArrayList<>();
		expectedTransactions.add(t1);
		expectedTransactions.add(t2);
		expectedTransactions.add(t3);

		// When
		when(accountRepository.findByAccountNumber("ES1111111111")).thenReturn(Optional.of(account));
		when(transactionRepository.findByAccountOrderByTimestampDesc(account)).thenReturn(expectedTransactions);
		List<Transaction> result = accountService.getTransactions("ES1111111111");

		// Then
		assertEquals(3, result.size());
		assertTrue(result.get(0).getTimestamp().isAfter(result.get(1).getTimestamp()));
		assertTrue(result.get(1).getTimestamp().isAfter(result.get(2).getTimestamp()));
		verify(accountRepository, times(1)).findByAccountNumber("ES1111111111");
		verify(transactionRepository, times(1)).findByAccountOrderByTimestampDesc(account);
	}

	// UNITARY TEST CUMULATIVE WITHDRAWAL
	// Test 1. Allows withdrawal when cumulative 24h amount stays below 5000
	@ParameterizedTest
	@MethodSource("cumulative24hAllowedScenarios")
	public void withdrawAllowsWhenCumulative24hBelowLimitTest(double alreadyWithdrawn, double amount) {
		// Given
		Account account = generateTestAccounts("ES1234567890", 10000.0);
		when(accountRepository.findByAccountNumber("ES1234567890")).thenReturn(Optional.of(account));
		when(accountRepository.save(any(Account.class))).thenReturn(account);
		when(transactionRepository.sumWithdrawalsAfter(eq(account), any(LocalDateTime.class)))
				.thenReturn(alreadyWithdrawn);

		// When
		Account result = accountService.withdraw("ES1234567890", amount, "within daily limit");

		// Then: succeeds and balance decreases
		assertEquals(10000.0 - amount, result.getBalance());
		verify(transactionRepository).save(any(Transaction.class));
	}

	// Test 2. When 24h limit is exceeded, no transaction is saved and no
	// notification is sent
	@Test
	public void withdrawDoesNotPersistOrNotifyWhenCumulative24hExceededTest() {
		// Given
		Account account = generateTestAccounts("ES1234567890", 10000.0);
		when(accountRepository.findByAccountNumber("ES1234567890")).thenReturn(Optional.of(account));
		when(transactionRepository.sumWithdrawalsAfter(eq(account), any(LocalDateTime.class)))
				.thenReturn(4500.0);

		// When + Then
		assertThrows(IllegalArgumentException.class, () -> {
			accountService.withdraw("ES1234567890", 600.0, "exceeds daily limit");
		});

		verify(transactionRepository, times(0)).save(any(Transaction.class));
		verifyNoInteractions(emailNotificationService);
		verifyNoInteractions(smsNotificationService);
	}

	// Test 3. Blocks withdrawal when cumulative 24h amount reaches or exceeds 5000
	@ParameterizedTest
	@MethodSource("cumulative24hBlockedScenarios")
	public void withdrawBlocksWhenCumulative24hReachesLimitTest(double alreadyWithdrawn, double amount) {
		// Given
		Account account = generateTestAccounts("ES1234567890", 10000.0);
		when(accountRepository.findByAccountNumber("ES1234567890")).thenReturn(Optional.of(account));
		when(transactionRepository.sumWithdrawalsAfter(eq(account), any(LocalDateTime.class)))
				.thenReturn(alreadyWithdrawn);

		// When + Then: rejected
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			accountService.withdraw("ES1234567890", amount, "reaches daily limit");
		});
		assertEquals("Daily withdrawal limit exceeded", e.getMessage());

		// And: no transaction is saved and no notification is sent
		verify(transactionRepository, times(0)).save(any(Transaction.class));
		verifyNoInteractions(emailNotificationService);
		verifyNoInteractions(smsNotificationService);
	}


	//UNITARY TEST USER BANNED
	//DEPOSIT TEST
	//Test 1. deposit throws exception if user is banned. 
	@Test
	public void depositThrowsExceptionWhenUserIsBannedTest() {
		// Given
		Account account = generateBannedAccount("ES9999999999", 1000.0);
		when(accountRepository.findByAccountNumber("ES9999999999")).thenReturn(Optional.of(account));
 
		// When / Then
		Exception e = assertThrows(IllegalStateException.class, () -> {
			accountService.deposit("ES9999999999", 100.0, "Banned deposit test");
		});
		assertEquals("The user is banned and cannot do any operations", e.getMessage());
		verify(accountRepository,never()).save(account);
	}

	//WITHDRAW TEST
	// Test 2. withdraw throws exception when emisor is banned.
	@Test
	public void withdrawThrowsExceptionWhenUserIsBannedTest() {
		// Given
		Account account = generateBannedAccount("ES9999999998", 1000.0);
		when(accountRepository.findByAccountNumber("ES9999999998")).thenReturn(Optional.of(account));
 
		// When / Then
		Exception e = assertThrows(IllegalStateException.class, () -> {
			accountService.withdraw("ES9999999998", 100.0, "Banned withdraw test");
		});
		assertEquals("The user is banned and cannot do any operations", e.getMessage());
		verify(accountRepository,never()).save(account);
	}

	//TRANSFER TESTS
	// Test 3. transfer throws exception when receiver is banned.
	@Test
	public void transferThrowsExceptionWhenReceiverIsBannedTest() {
		// Given
		Account fromAccount = generateTestAccounts("ES9999999995", 1000.0);
		Account toAccount = generateBannedAccount("ES9999999994", 500.0);
 
		when(accountRepository.findByAccountNumber("ES9999999995")).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findByAccountNumber("ES9999999994")).thenReturn(Optional.of(toAccount));
 
		// When / Then
		Exception e = assertThrows(IllegalStateException.class, () -> {
			accountService.transfer("ES9999999995", "ES9999999994", 100.0);
		});
		assertEquals("The recipient account user is banned and cannot recieve transfers", e.getMessage());
		verify(accountRepository,never()).save(toAccount);
	}
	
	// Test 4. transfer throws exception when receiver is banned.
	@Test
	public void transferThrowsExceptionWhenEmisorIsBannedTest() {
		// Given
		Account fromAccount = generateBannedAccount("ES9999999995", 1000.0);
		Account toAccount = generateTestAccounts("ES9999999994", 500.0);
 
		when(accountRepository.findByAccountNumber("ES9999999995")).thenReturn(Optional.of(fromAccount));
		when(accountRepository.findByAccountNumber("ES9999999994")).thenReturn(Optional.of(toAccount));
 
		// When / Then
		Exception e = assertThrows(IllegalStateException.class, () -> {
			accountService.transfer("ES9999999995", "ES9999999994", 100.0);
		});
		assertEquals("The sender account user is banned and cannot do any operations", e.getMessage());
		verify(accountRepository,never()).save(fromAccount);
	}
}