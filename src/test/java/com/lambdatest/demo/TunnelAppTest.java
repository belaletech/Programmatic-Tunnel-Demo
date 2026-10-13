package com.lambdatest.demo;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class TunnelAppTest {

    private static final String USERNAME = System.getenv().getOrDefault("LT_USERNAME", "");
    private static final String ACCESS_KEY = System.getenv().getOrDefault("LT_ACCESS_KEY", "");
    private static final String APP_ID = System.getenv().getOrDefault("LT_APP_ID", "lt://");
    private static final String TUNNEL_NAME = System.getenv().getOrDefault("LT_TUNNEL_NAME", "demo-tunnel");
    private static final String TUNNEL_MODE = System.getenv().getOrDefault("TUNNEL_MODE", "binary");
    private static final String GRID_URL = "https://" + USERNAME + ":" + ACCESS_KEY + "@mobile-hub.lambdatest.com/wd/hub";

    private static TunnelManager tunnel;
    private AndroidDriver driver;

    @BeforeSuite
    public void startTunnel() throws Exception {
        tunnel = new TunnelManager(TUNNEL_MODE);
        tunnel.start(USERNAME, ACCESS_KEY, TUNNEL_NAME);
    }

    @BeforeClass
    @Parameters({"device", "version", "platform"})
    public void setUp(String device, String version, String platform) throws Exception {
        Map<String, Object> ltOptions = new HashMap<>();
        ltOptions.put("w3c", true);
        ltOptions.put("platformName", platform);
        ltOptions.put("deviceName", device);
        ltOptions.put("platformVersion", version);
        ltOptions.put("app", APP_ID);
        ltOptions.put("isRealMobile", true);
        ltOptions.put("tunnel", true);
        ltOptions.put("tunnelName", TUNNEL_NAME);
        ltOptions.put("devicelog", true);
        ltOptions.put("network", true);
        ltOptions.put("idleTimeout", 60);
        ltOptions.put("build", "Tunnel Demo - " + TUNNEL_MODE + " mode");
        ltOptions.put("name", "App via tunnel");
        ltOptions.put("project", "TunnelDemo");

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("lt:options", ltOptions);

        driver = new AndroidDriver(new URL(GRID_URL), caps);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        System.out.println("[Test] Session started: " + driver.getSessionId());
    }

    @Test
    public void sessionRunsThroughTunnel() throws InterruptedException {
        Assert.assertNotNull(driver.getSessionId(), "Session should be created");
        System.out.println("[Test] Display density: " + driver.getDisplayDensity());
        System.out.println("[Test] Current package: " + driver.getCurrentPackage());
        Thread.sleep(12000);

        // Add app steps that hit your internal/local backend here, e.g.:
        // click(driver.findElement(AppiumBy.id("com.example:id/login")));
        // sendKeys(driver.findElement(AppiumBy.id("com.example:id/user")), "demo");
    }

    // ---------- helpers ----------

    private void click(WebElement el) {
        el.click();
    }

    private void sendKeys(WebElement el, String text) {
        el.clear();
        el.sendKeys(text);
    }

    // ---------- teardown ----------

    @AfterMethod
    public void markStatus(ITestResult result) {
        if (driver == null) return;
        String status = result.isSuccess() ? "passed" : "failed";
        try {
            ((JavascriptExecutor) driver).executeScript("lambda-status=" + status);
        } catch (Exception e) {
            System.out.println("[Test] Could not set lambda-status: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @AfterSuite(alwaysRun = true)
    public void stopTunnel() {
        if (tunnel != null) {
            tunnel.stop();
        }
    }
}
