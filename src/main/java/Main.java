import com.yandex.disk.rest.Credentials;
import com.yandex.disk.rest.ResourcesArgs;
import com.yandex.disk.rest.RestClient;
import com.yandex.disk.rest.exceptions.ServerException;
import com.yandex.disk.rest.exceptions.http.HttpCodeException;
import com.yandex.disk.rest.json.Link;
import com.yandex.disk.rest.json.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by ssr on 02.06.17.
 */
public class Main {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy--HH-mm");

    public static void main(String[] args) throws IOException, ServerException, InterruptedException {
        Properties properties = readProperties(args);

        Credentials credentials = new Credentials(properties.getProperty("user"), properties.getProperty("otoken"));

        RestClient client = new RestClient(credentials);


        String backupFolderPath = properties.getProperty("yadisk.backup.folder");
        try {
            Resource bacupFolder = client.getResources(new ResourcesArgs.Builder().setPath(backupFolderPath).build());
            //TODO: продумать механизм удаления старых бэкапов
        } catch (HttpCodeException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                //creating folder
                client.makeFolder(backupFolderPath);
            }
        }


        final boolean removeAfterBackup = BooleanUtils.toBoolean(properties.getProperty("remove.after.backup", "false"));

        Map<String, String> propToFolderName = propertieToName(properties);

        int processors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = processors;
        if (propToFolderName.size() < processors) {
            threadPoolSize = propToFolderName.size();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

        List<Callable<Void>> toExecute = new ArrayList<>();

        for (String propName : propToFolderName.keySet()) {
            toExecute.add(() -> {
                String path = properties.getProperty(propName);
                Path toArchive = Paths.get(path);
                Path archivePath;
                String zipName = propToFolderName.get(propName) + "-" + DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip";
                if (Files.isDirectory(toArchive)) {
                    archivePath = Paths.get(toArchive.toString(), zipName);
                } else {
                    Path parent = toArchive.getParent();
                    archivePath = Paths.get(parent.toString(), zipName);
                }
                archive(toArchive, archivePath);
                Link uploadLink = client.getUploadLink(backupFolderPath + "/" + zipName, false);
                client.uploadFile(uploadLink, false, archivePath.toFile(), null);
                if (removeAfterBackup) {
                    Files.delete(archivePath);
                }
                return null;
            });
        }
        executorService.invokeAll(toExecute);
        // TODO: 04.06.17 проверка на то что все прошло хорошо, если нет, расказать об этом
        executorService.shutdown();
    }

    private static Map<String, String> propertieToName(Properties properties) {
        Map<String, String> propToFolderName = new HashMap<>();

        Enumeration<String> enumeration = (Enumeration<String>) properties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String propertyName = enumeration.nextElement();
            if (!StringUtils.startsWith(propertyName, "backup")) {
                continue;
            }
            String folderName = StringUtils.substringAfter(propertyName, ".");
            if (StringUtils.isBlank(folderName) || StringUtils.contains(folderName, ".")) {
                throw new IllegalArgumentException("Property name " + propertyName + " not valid.");
            }
            String propValue = properties.getProperty(propertyName);
            if (StringUtils.isBlank(propValue) || Files.notExists(Paths.get(propValue))) {
                throw new IllegalArgumentException("Not valid value of propertie: " + propertyName + "  - " + propValue + ". Value empty or path not exist");
            }

            propToFolderName.put(propertyName, folderName);
        }
        return propToFolderName;
    }

    public static void archive(Path folderToArchive, Path outputPath) {
        try {
            FileOutputStream dest = new
                    FileOutputStream(outputPath.toFile());
            CheckedOutputStream checksum = new
                    CheckedOutputStream(dest, new Adler32());

            ZipOutputStream out = new
                    ZipOutputStream(new
                    BufferedOutputStream(checksum));
            Files.walk(folderToArchive).forEach(p -> {
                if (!Files.isDirectory(p)) {
                    ZipEntry entry = new ZipEntry(folderToArchive.toAbsolutePath().relativize(p).toString());
                    try {
                        out.putNextEntry(entry);
                        out.write(Files.readAllBytes(p));
                    } catch (IOException e) {
                        throw new RuntimeException("Reading " + p + " fail", e);
                    }
                }
            });

            System.out.println(outputPath.toAbsolutePath().toString());

            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static Properties readProperties(String[] args) throws IOException {
        Properties properties = new Properties();

        Path propertiesPath;
        if (args != null && args.length > 0 && args[0] != null) {
            propertiesPath = Paths.get(args[0]);
        } else {
            propertiesPath = Paths.get("properties");
        }
        if (Files.exists(propertiesPath)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
                properties.load(bufferedReader);
            }
        } else {
            throw new IllegalArgumentException("Properties not found: " + propertiesPath);
        }
        if (!properties.containsKey("otoken")) {
            throw new IllegalArgumentException("otoken not defined");
        }
        if (!properties.containsKey("user")) {
            throw new IllegalArgumentException("user not defined");
        }
        if (!properties.containsKey("yadisk.backup.folder")) {
            throw new IllegalArgumentException("yadisk.backup.folder not defined");
        }
        return properties;
    }
}
