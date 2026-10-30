package es.codeurjc.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.WebDriver;

public class SmokeTest {

    private static final String AZURE_URL = System.getProperty("azure.url", "https://banking-app-production-h4awerbsgwd5aqdd.polandcentral-01.azurewebsites.net");
    private static final String EXPECTED_VERSION = System.getProperty("app.version", "1.2.0");

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    public void setup() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
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

    @Test
    public void smokeTest() {
        driver.get(AZURE_URL + "/login");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("version")));
        String versionText = driver.findElement(By.id("version")).getText();

        assertTrue(versionText.contains(EXPECTED_VERSION),
            "Expected version " + EXPECTED_VERSION + " but found: " + versionText);
    }
}
