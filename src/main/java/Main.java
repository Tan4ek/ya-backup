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
        String pathToProperties = "properties";
        if (args != null && args.length > 0 && args[0] != null) {
            pathToProperties = args[0];
        }

        Configuration configuration = readConfiguration(pathToProperties);

        Credentials credentials = new Credentials(configuration.getUser(), configuration.getToken());

        RestClient client = new RestClient(credentials);


        String backupFolderPath = configuration.getYandexDiskFolder();
        try {
            Resource bacupFolder = client.getResources(new ResourcesArgs.Builder().setPath(backupFolderPath).build());
            //TODO: продумать механизм удаления старых бэкапов
        } catch (HttpCodeException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                //creating folder
                client.makeFolder(backupFolderPath);
            }
        }

        final boolean removeAfterBackup = configuration.isRemoveArchiveAfterBackup();

        List<Callable<Void>> toExecute = new ArrayList<>();

        Map<Path, String> backupFolders = configuration.getBackupFolders();

        int processors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = processors;
        if (backupFolders.size() < processors) {
            threadPoolSize = backupFolders.size();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

        for (Path toArchive : backupFolders.keySet()) {
            toExecute.add(() -> {
                String zipName = backupFolders.get(toArchive) + "-" + DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip";
                Path archivePath = Files.isDirectory(toArchive) ? Paths.get(toArchive.toString(), zipName) : Paths.get(toArchive.getParent().toString(), zipName);
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

    private static Configuration readConfiguration(String pathToProperties) throws IOException {
        Properties properties = new Properties();

        Path propertiesPath = Paths.get(pathToProperties);
        if (Files.notExists(propertiesPath)) {
            throw new IllegalArgumentException("Not valid path to properties or properties does not exist");
        }
        try (BufferedReader bufferedReader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
            properties.load(bufferedReader);
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

        Map<Path, String> propToFolderName = getPathStringMap(properties);
        return new Configuration(properties.getProperty("otoken"),
                properties.getProperty("user"),
                BooleanUtils.toBoolean(properties.getProperty("remove.after.backup", "true")),
                properties.getProperty("yadisk.backup.folder"),
                propToFolderName);
    }

    private static Map<Path, String> getPathStringMap(Properties properties) {
        Map<Path, String> propToFolderName = new HashMap<>();

        Enumeration<String> enumeration = (Enumeration<String>) properties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String propertyName = enumeration.nextElement();
            if (!StringUtils.startsWith(propertyName, "backup")) {
                continue;
            }
            String folderName = StringUtils.substringAfter(propertyName, ".");
            if (StringUtils.isBlank(folderName)) {
                throw new IllegalArgumentException("Property name " + propertyName + " not valid.");
            }
            String propValue = properties.getProperty(propertyName);
            if (StringUtils.isBlank(propValue) || Files.notExists(Paths.get(propValue))) {
                throw new IllegalArgumentException("Not valid value of propertie: " + propertyName + "  - " + propValue + ". Value empty or path not exist");
            }

            propToFolderName.put(Paths.get(propValue), folderName);
        }
        return propToFolderName;
    }

    private static class Configuration {
        private final String token;
        private final String user;
        private final boolean removeArchiveAfterBackup;
        private final String yandexDiskFolder;
        //key - Path путь до файла, value - название выходного архива
        private final Map<Path, String> backupFolders;

        public Configuration(String token, String user, boolean removeArchiveAfterBackup, String yandexDiskFolder, Map<Path, String> backupFolders) {
            this.token = token;
            this.user = user;
            this.removeArchiveAfterBackup = removeArchiveAfterBackup;
            this.yandexDiskFolder = yandexDiskFolder;
            this.backupFolders = Collections.unmodifiableMap(new HashMap<>(backupFolders));
        }

        public String getToken() {
            return token;
        }

        public String getUser() {
            return user;
        }

        public boolean isRemoveArchiveAfterBackup() {
            return removeArchiveAfterBackup;
        }

        public String getYandexDiskFolder() {
            return yandexDiskFolder;
        }

        public Map<Path, String> getBackupFolders() {
            return backupFolders;
        }
    }
}
