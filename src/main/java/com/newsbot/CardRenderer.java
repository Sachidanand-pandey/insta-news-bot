package com.newsbot;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Uses Selenium (headless Chrome) to turn HTML slides into JPEG images.
 * Instagram's publishing API accepts JPEG only, so the PNG screenshot is converted.
 */
final class CardRenderer implements AutoCloseable {
    private final WebDriver driver;
    private final Path outDir;

    CardRenderer(Path outDir) throws IOException {
        this.outDir = outDir;
        Files.createDirectories(outDir);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
                "--hide-scrollbars", "--force-device-scale-factor=1", "--window-size=1200,1500");
        String chromeBin = Config.get("CHROME_BIN", null);
        if (chromeBin != null) {
            options.setBinary(chromeBin);
        }
        this.driver = new ChromeDriver(options);
    }

    /** Renders one slide and returns the path of the resulting .jpg. */
    Path render(String html, String name) throws IOException {
        Path htmlFile = outDir.resolve(name + ".html");
        Files.writeString(htmlFile, html);

        driver.get(htmlFile.toUri().toString());
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

        File png = driver.findElement(By.id("card")).getScreenshotAs(OutputType.FILE);
        Path jpg = outDir.resolve(name + ".jpg");
        writeJpeg(ImageIO.read(png), jpg);
        Files.deleteIfExists(htmlFile);
        return jpg;
    }

    private static void writeJpeg(BufferedImage src, Path target) throws IOException {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.92f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    @Override
    public void close() {
        driver.quit();
    }
}
