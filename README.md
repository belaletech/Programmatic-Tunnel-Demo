# LambdaTest Tunnel + Appium (Java / TestNG) — Programmatic Tunnel Demo

This project shows how to **start a LambdaTest tunnel from code**, run an Appium test on a
LambdaTest **real Android device** through that tunnel, and **stop the tunnel** when the suite ends.
No manual tunnel start is needed.

```
@BeforeSuite  → start tunnel (waits until it is connected)
@BeforeClass  → create Appium session with tunnel: true + tunnelName
@Test         → your app steps (traffic from the device routes through your machine)
@AfterMethod  → mark test passed/failed on the dashboard
@AfterClass   → quit driver
@AfterSuite   → stop tunnel
```

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java (JDK) | 11 or newer | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| LambdaTest tunnel binary | latest | `./LT --version` |

**macOS (Homebrew):**

```bash
brew install maven
brew install openjdk@17     # only if Java is missing
```

---

## 2. Project structure

```
lt-tunnel-appium-demo/
├── LT                          ← tunnel binary (you add this, see step 3)
├── pom.xml
├── testng.xml                  ← device / OS version / platform
└── src/test/
    ├── java/com/lambdatest/demo/
    │   ├── TunnelManager.java  ← starts/stops the tunnel
    │   └── TunnelAppTest.java  ← Appium test
    └── resources/logback-test.xml
```

---

## 3. Add the tunnel binary

1. Download the LambdaTest tunnel binary for your OS from the LambdaTest docs (Tunnel → Download).
2. Unzip it and place the `LT` file in the **project root** (next to `pom.xml`).
3. Make it executable:

```bash
cd lt-tunnel-appium-demo

# If macOS saved it as "LT 2" (duplicate download), rename it first:
mv "LT 2" LT 2>/dev/null

chmod +x LT
xattr -d com.apple.quarantine LT 2>/dev/null   # macOS only: avoids "unidentified developer" block
./LT --version                                  # should print: LT version x.y.z
```

> The binary can live anywhere. If it's not in the project root, point to it with
> `export LT_BINARY=/full/path/to/LT`.

> Windows: use `LT.exe` and set `LT_BINARY=C:\path\to\LT.exe`.

---

## 4. Configure credentials and app

Set these in the **same terminal** you will run Maven from:

```bash
export LT_USERNAME=<your_lambdatest_username>
export LT_ACCESS_KEY=<your_lambdatest_access_key>
export LT_APP_ID=lt://APP1234567890          # app uploaded to LambdaTest
export LT_TUNNEL_NAME=my-tunnel              # any unique name
```

Username and access key are on your LambdaTest profile / Automation dashboard.

Device, OS version and platform are set in `testng.xml`:

```xml
<parameter name="device" value="Pixel 10"/>
<parameter name="version" value="16"/>
<parameter name="platform" value="android"/>
```

> Don't hardcode credentials in code you commit. Use the env vars above.

---

## 5. Run

```bash
TUNNEL_MODE=binary mvn clean test
```

First run downloads Maven dependencies, so it takes a minute or two.

### Two tunnel modes

| `TUNNEL_MODE` | How the tunnel starts | Needs `LT` binary in project? |
|---|---|---|
| `binary` (recommended) | Launches the `LT` binary from Java (`ProcessBuilder`) and waits for it to connect | Yes |
| `sdk` | Uses the LambdaTest Tunnel Java SDK (`com.lambdatest.tunnel.Tunnel`), which downloads the binary itself | No |

```bash
TUNNEL_MODE=sdk mvn clean test
```

---

## 6. Expected output

```
[Tunnel] Starting in 'binary' mode, name=my-tunnel
[LT] INFO   LambdaTest Tunnel version: 3.x.x
[LT] INFO   TCP Mode is supported
[LT] INFO   You can start testing now
[LT] INFO   Tunnel ID: 12345678
[Tunnel] Started. Check Tunnels page on the dashboard for: my-tunnel
[Test] Session started: <session-id>
[Test] Display density: 420
...
[Tunnel] Binary tunnel stopped
```

While it runs:
- The tunnel shows as **Active** on the LambdaTest **Tunnels** page.
- `[LT] handling connect request for URL ...` lines show device traffic going through the tunnel.
- The session appears on the App Automation dashboard under build **"Tunnel Demo - binary mode"**.

---

## 7. Using this in your own framework

1. Copy `TunnelManager.java` into your project.
2. Start it once before all tests and stop it after all tests:

```java
private static TunnelManager tunnel;

@BeforeSuite
public void startTunnel() throws Exception {
    tunnel = new TunnelManager("binary");   // or "sdk"
    tunnel.start(USERNAME, ACCESS_KEY, TUNNEL_NAME);
}

@AfterSuite(alwaysRun = true)
public void stopTunnel() {
    if (tunnel != null) tunnel.stop();
}
```

3. Add these to your `lt:options` capabilities (the name must match):

```java
ltOptions.put("tunnel", true);
ltOptions.put("tunnelName", TUNNEL_NAME);
```

4. If you use `sdk` mode, add the dependency from this `pom.xml` (`com.github.lambdatest:lambdatest-tunnel-binary`)
   **with the Selenium exclusion and the `selenium-bom` pin** — see Troubleshooting below.

---

## 8. Optional settings

| Env var | Purpose | Example |
|---|---|---|
| `LT_BINARY` | Path to tunnel binary (default: `./LT`) | `/opt/lt/LT` |
| `LT_TUNNEL_EXTRA_ARGS` | Extra tunnel flags (proxy, etc.) | `--proxy-host 10.0.0.1 --proxy-port 8080` |
| `LT_TUNNEL_READY_TEXT` | Line that means "connected" | `You can start testing now` |

Behind a corporate proxy:

```bash
export LT_TUNNEL_EXTRA_ARGS="--proxy-host <host> --proxy-port <port>"
```

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `zsh: command not found: mvn` | Install Maven: `brew install maven` |
| `Set LT_BINARY ... Current: ...` | `LT` binary not found or not executable. Put it in the project root and run `chmod +x LT`, or `export LT_BINARY=/full/path/to/LT` in the same terminal. |
| macOS: "LT cannot be opened" / "unidentified developer" | `xattr -d com.apple.quarantine LT` |
| `cannot access org.openqa.selenium.ContextAware` / `html5.LocationContext` | Selenium version too new for Appium java-client 9.x. Keep the `selenium-bom` pin (`4.23.0`) in `dependencyManagement` and the Selenium exclusion on the tunnel dependency, as in this `pom.xml`. |
| `... was not found in https://repo.maven.apache.org ... during a previous attempt` | Maven cached a failed lookup. Re-run with `mvn -U clean test`. |
| `Tunnel did not become ready within 120s` | Check the `[LT]` lines above it. Usually a firewall/proxy: try `LT_TUNNEL_EXTRA_ARGS` with proxy flags, or test on a different network to confirm. |
| Tunnel never shows on the dashboard (SDK mode) | Try `TUNNEL_MODE=binary`. SDK logs are at DEBUG via `logback-test.xml`; if your project has its own `logback.xml`, add `<logger name="com.lambdatest" level="DEBUG"/>`. |
| Session fails with tunnel error | Make sure `tunnelName` in caps exactly matches `LT_TUNNEL_NAME`, and no other tunnel with the same name is running. |

---

Need help? Share the full console output (including the `[LT]` lines) with LambdaTest support.
