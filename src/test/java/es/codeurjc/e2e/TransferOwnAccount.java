package es.codeurjc.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import es.codeurjc.BankingApplication;


/*
    THIS TEST WORKS WHEN THE NIGHTLY WORKFLOW RUNS
*/
@SpringBootTest(classes = BankingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransferOwnAccount {
    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        String browser = System.getProperty("browser", "chrome").toLowerCase();

        switch (browser) {
            case "firefox" -> {
                FirefoxOptions options = new FirefoxOptions();
                options.addArguments("--headless");
                this.driver = new FirefoxDriver(options);
            }
            case "edge" -> {
                EdgeOptions options = new EdgeOptions();
                options.addArguments("--headless");
                this.driver = new EdgeDriver(options);
            }
            case "safari" -> {
                this.driver = new SafariDriver();
            }
            default -> {
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
            }
        }

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

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("logoutButton")));
    }

    private String getAccountBalance(String accountNumber) {
        driver.get(baseUrl() + "/dashboard");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("balance-" + accountNumber)));

        return driver.findElement(By.id("balance-" + accountNumber)).getText();
    }

    private void assertBalanceChanged(String accountNumber, String initialBalance, String amount, Boolean addBoolean) {
        String balanceAfter = getAccountBalance(accountNumber);
        double balanceInitial = Double.parseDouble(initialBalance);
        double amountDouble = Double.parseDouble(amount);
        String expectedBalance;

        if (addBoolean == true) {
            expectedBalance = String.valueOf(balanceInitial + amountDouble);
        } else {
            expectedBalance = String.valueOf(balanceInitial - amountDouble);
        }

        assertEquals(expectedBalance, balanceAfter);
    }

    // A transfer can be made between own accounts (from one checking account to one savings)
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
        assertBalanceChanged(from, initialBalanceFromAccount, amount, false);
        assertBalanceChanged(to, initialBalanceToAccount, amount, true);
    }
}
