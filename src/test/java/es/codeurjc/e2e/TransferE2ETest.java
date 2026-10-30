package es.codeurjc.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import es.codeurjc.BankingApplication;

/* SYSTEM TESTS
    1. A transfer can be made between your own accounts (e.g., from a checking account to a savings account) - Alejandro
    2. A transfer can be made between accounts belonging to different users - Matías
    3. A transfer cannot be made from an account to the same account. - Carla
    4. A transfer cannot be made if there is an insufficient balance. - Raúl
    5. A transfer cannot be made if the amount is negative. - Alejandro
    6. A transfer cannot be made if the amount exceeds €20,000. - Carla/Raúl
    7. A transfer cannot be made to an invalid or non-existent account. - Matías
*/

@SpringBootTest(classes = BankingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransferE2ETest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        ChromeOptions options = new ChromeOptions();

        Map<String, Object> prefs = new HashMap<>();


        prefs.put("profile.password_manager_leak_detection", false);

        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        options.setExperimentalOption("prefs", prefs);

        options.addArguments("--window-size=1920,1080");
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--remote-allow-origins=*");

        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    public void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void login(String user, String pass) {
        driver.get(baseUrl() + "/login");

        WebElement usernameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        WebElement passwordInput = driver.findElement(By.id("password"));
        WebElement loginButton = driver.findElement(By.id("loginButton"));

        usernameInput.clear();
        usernameInput.sendKeys(user);

        passwordInput.clear();
        passwordInput.sendKeys(pass);

        loginButton.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("logoutButton"))); // Is a way of checking if the login was correct
    }

    private String getAccountBalance(String accountNumber) {
        driver.get(baseUrl() + "/dashboard");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("balance-" + accountNumber)));

        return driver.findElement(By.id("balance-" + accountNumber)).getText();
    }

    private void assertBalanceUnchanged(String accountNumber, String balanceBefore) {
        String balanceAfter = getAccountBalance(accountNumber);
        assertEquals(balanceBefore, balanceAfter);
    }

    private void assertBalanceChanged(String accountNumber,  String initialBalance, String amount, Boolean addBoolean ) {
        String balanceAfter = getAccountBalance(accountNumber);
        double balanceInitial = Double.parseDouble(initialBalance); 
        double amountDouble = Double.parseDouble(amount);
        String expectedBalance;

        if(addBoolean == true){
            expectedBalance = String.valueOf(balanceInitial + amountDouble); 
        }else{
            expectedBalance = String.valueOf(balanceInitial - amountDouble); 
        }
        
        assertEquals(expectedBalance, balanceAfter);
    }

    // Test 1. A transfer can be made between own accounts (from one checking account to one savings)
    @Test
    public void transferOwnAccount() {
        // Sending 100 from own checking account to own saving account
        String from = "ES0001234567";
        String to = "ES0001234568";
        String amount = "100";

        // Login User from
        login("customer", "Cu5t0m3r");

        // Get balance from account before transfer
        String initialBalanceFromAccount = getAccountBalance(from);

        // Get balance to account before transfer
        String initialBalanceToAccount = getAccountBalance(to);

        // Go to Transfer
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Complete transfer data
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));

        // From account
        WebElement selectElement = driver.findElement(By.id("fromAccount"));
        Select select = new Select(selectElement);
        select.selectByValue(from);

        // To account
        WebElement toAccount = driver.findElement(By.id("toAccount"));
        toAccount.click();
        toAccount.clear();
        toAccount.sendKeys(to);

        // Amount
        WebElement inputAmount = driver.findElement(By.id("amount"));
        inputAmount.click();
        inputAmount.clear();
        inputAmount.sendKeys(amount);

        WebElement transferButton = driver.findElement(By.id("transferButton"));
        transferButton.click();

        // Verify result
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("alert-success")));
        String result = driver.findElement(By.className("alert-success")).getText();
        assertTrue(result.contains("Transfer completed successfully"));

        //Verify balance was changed
        assertBalanceChanged(from, initialBalanceFromAccount, amount , false);
        assertBalanceChanged(to, initialBalanceToAccount, amount , true);
    }

    // Test 2. A transfer can be made between accounts belonging to different users
    @Test
    public void transferDiferentUsers() {
        // Sending 100 from user1 (juan) to user2 (maria)
        String from = "ES0001234567";
        String to = "ES0002345678";
        String amount = "100";

        // Get balance of destination account
        login("maria", "maria123");
        String initialBalanceToAccount = getAccountBalance(to);
        driver.findElement(By.id("logoutButton")).click();

        // Login User from
        login("customer", "Cu5t0m3r");

        String initialBalanceFromAccount = getAccountBalance(from);

        // Go to Transferir
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Complete transfer data
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));

        // From account
        WebElement selectElement = driver.findElement(By.id("fromAccount"));
        Select select = new Select(selectElement);
        select.selectByValue(from);

        // To account
        WebElement toAccount = driver.findElement(By.id("toAccount"));
        toAccount.click();
        toAccount.clear();
        toAccount.sendKeys(to);

        // Amount
        WebElement inputAmount = driver.findElement(By.id("amount"));
        inputAmount.click();
        inputAmount.clear();
        inputAmount.sendKeys(amount);

        WebElement transferButton = driver.findElement(By.id("transferButton"));
        transferButton.click();

        // Verify result
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("alert-success")));
        String result = driver.findElement(By.className("alert-success")).getText();

        assertTrue(result.contains("Transfer completed successfully"));

        // Verify balances after transfer
        assertBalanceChanged(from,initialBalanceFromAccount,amount,false);

        driver.findElement(By.id("logoutButton")).click();
        login("maria", "maria123");
        assertBalanceChanged(to, initialBalanceToAccount,amount,true);
    }

    // Test 3. A transfer cannot be made from an account to the same account
    @Test
    public void transferSameAccount() {
        String accountNumber = "ES0001234567";
        String amount = "1500";

        login("customer", "Cu5t0m3r");

        String initialBalance = getAccountBalance(accountNumber);

        // Try transference
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));
        WebElement selectElement = driver.findElement(By.id("fromAccount"));
        Select select = new Select(selectElement);
        select.selectByValue(accountNumber);

        WebElement toAccount = driver.findElement(By.id("toAccount"));
        toAccount.click();
        toAccount.clear();
        toAccount.sendKeys(accountNumber);

        WebElement inputAmount = driver.findElement(By.id("amount"));
        inputAmount.click();
        inputAmount.clear();
        inputAmount.sendKeys(amount);

        driver.findElement(By.id("transferButton")).click();

        // Assert transference was not done
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("alert-danger")));
        String result = driver.findElement(By.className("alert-danger")).getText();
        assertTrue(result.contains("Cannot transfer to same account"));

        // Verify balance was not changed
        assertBalanceUnchanged(accountNumber, initialBalance);
    }

    // Test 4. A transfer cannot be made if there is an insufficient balance
    @Test
    public void transferFailsWhenInsufficientFundsTest() {
        String fromAccount = "ES0001234567";
        String toAccount = "ES0002345678";

        login("customer", "Cu5t0m3r");

        // Get balance before transfer
        String initialBalanceFromAccount = getAccountBalance(fromAccount);

        // Go to transfer page
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Select origin account
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));
        Select fromAccountSelect = new Select(driver.findElement(By.id("fromAccount")));
        fromAccountSelect.selectByValue(fromAccount);

        // Enter destination account
        driver.findElement(By.id("toAccount")).sendKeys(toAccount);

        // Enter amount greater than balance
        driver.findElement(By.id("amount")).sendKeys("6000");

        // Submit
        driver.findElement(By.id("transferButton")).click();

        // Check error message
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".alert-danger")));
        String errorMessage = driver.findElement(By.cssSelector(".alert-danger")).getText();
        assertEquals("Insufficient funds", errorMessage);

        // Check balance has not changed
        assertBalanceUnchanged(fromAccount, initialBalanceFromAccount);
    }

    //Test 5. A transfer cannot be made if the amount is negative
    @Test
    public void transferNegativeAmount() {
        //Trying to transfer a negatice amount 
        String fromAccount = "ES0001234567";
        String toAccount = "ES0002345678";

        // Login User from
        login("customer", "Cu5t0m3r");

        // Get balance before transfer
        String initialBalanceFromAccount = getAccountBalance(fromAccount);

       // Go to transfer page
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Select origin account
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));
        Select fromAccountSelect = new Select(driver.findElement(By.id("fromAccount")));
        fromAccountSelect.selectByValue(fromAccount);

        // Enter destination account
        driver.findElement(By.id("toAccount")).sendKeys(toAccount);

        // Enter amount greater than balance
        driver.findElement(By.id("amount")).sendKeys("-1");

        // Submit
        driver.findElement(By.id("transferButton")).click();

        // Check error message
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".alert-danger")));
        String errorMessage = driver.findElement(By.cssSelector(".alert-danger")).getText();
        assertEquals("Amount must be positive", errorMessage);
        
        // Check balance has not changed
        assertBalanceUnchanged(fromAccount, initialBalanceFromAccount);

    }

    
    // Test 6. A transfer cannot be made if the amount exceeds €20,000
    @Test
    public void transferFailsWhenAmountExceedsLimitTest(){
        login("customer", "Cu5t0m3r");

        // Get balance before transfer
        driver.get(baseUrl() + "/dashboard");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("balance-ES0001234567")));
        double balanceBefore = Double.parseDouble(driver.findElement(By.id("balance-ES0001234567")).getText());

        // Go to transfer page
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Select origin account
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));
        Select fromAccountSelect = new Select(driver.findElement(By.id("fromAccount")));
        fromAccountSelect.selectByValue("ES0001234567");

        // Enter destination account
        driver.findElement(By.id("toAccount")).sendKeys("ES0002345678");

        // Enter amount greater than limit (20000€)
        driver.findElement(By.id("amount")).sendKeys("25000");

        // Submit
        driver.findElement(By.id("transferButton")).click();

        // Check error message
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".alert-danger")));
        String errorMessage = driver.findElement(By.cssSelector(".alert-danger")).getText();
        assertEquals("Amount exceeds maximum transfer limit", errorMessage);

        // Check balance has not changed
        driver.get(baseUrl() + "/dashboard");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("balance-ES0001234567")));
        double balanceAfter = Double.parseDouble(driver.findElement(By.id("balance-ES0001234567")).getText());
        assertEquals(balanceBefore, balanceAfter);
    }

    // Test 7. A transfer cannot be made to an invalid or non-existent account
    @Test
    public void transferInvalidAccount() {
        // Try to send 100 from an existant account to an invalid account
        String from = "ES0001234567";
        String to = "ES0000000023"; // random number
        String amount = "100";

        // Login User from
        login("customer", "Cu5t0m3r");

        String initialBalanceFromAccount = getAccountBalance(from);

        // Go to Transferir
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Money"))).click();

        // Complete transfer data
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fromAccount")));

        // From account
        WebElement selectElement = driver.findElement(By.id("fromAccount"));
        Select select = new Select(selectElement);
        select.selectByValue(from);

        // To account
        WebElement toAccount = driver.findElement(By.id("toAccount"));
        toAccount.click();
        toAccount.clear();
        toAccount.sendKeys(to);

        // Amount
        WebElement inputAmount = driver.findElement(By.id("amount"));
        inputAmount.click();
        inputAmount.clear();
        inputAmount.sendKeys(amount);

        WebElement transferButton = driver.findElement(By.id("transferButton"));
        transferButton.click();

        // Check error message
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".alert-danger")));
        String errorMessage = driver.findElement(By.cssSelector(".alert-danger")).getText();
        assertEquals("Account not found", errorMessage);

        // Check balance has not changed
        assertBalanceUnchanged(from, initialBalanceFromAccount);
    }
}
