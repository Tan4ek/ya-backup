package ru.ssr.yabackup;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.internal.DefaultConverterFactory;
import com.yandex.disk.rest.Credentials;
import com.yandex.disk.rest.ResourcesArgs;
import com.yandex.disk.rest.RestClient;
import com.yandex.disk.rest.exceptions.ServerException;
import com.yandex.disk.rest.exceptions.http.HttpCodeException;
import com.yandex.disk.rest.json.Link;
import com.yandex.disk.rest.json.Resource;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
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
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm");

    public static void main(String[] args) throws IOException, ServerException, InterruptedException {
        ConsoleParameters consoleParameters = new ConsoleParameters();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(consoleParameters)
                .addConverterFactory(new DefaultConverterFactory())
                .build();
        jCommander.parse(args);

        Credentials credentials = new Credentials(consoleParameters.getUser(), consoleParameters.getToken());

        RestClient client = new RestClient(credentials);


        String backupFolderPath = consoleParameters.getYandexDiskFolder();
        try {
            Resource bacupFolder = client.getResources(new ResourcesArgs.Builder().setPath(backupFolderPath).build());
            //TODO: продумать механизм удаления старых бэкапов
        } catch (HttpCodeException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                //creating folder
                client.makeFolder(backupFolderPath);
            } else {
                throw e;
            }
        }

        final boolean removeAfterBackup = consoleParameters.isRemoveArchiveAfterBackup();

        List<Callable<Void>> toExecute = new ArrayList<>();

        Map<Path, String> backupFolders = pathStringMap(consoleParameters);

        int processors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = backupFolders.size() < processors ? backupFolders.size() : processors;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

        for (Path toArchive : backupFolders.keySet()) {
            toExecute.add(() -> {
                String zipName = backupFolders.get(toArchive) + "-" + DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip";
                Path archivePath = Files.isDirectory(toArchive) ? Paths.get(toArchive.toString(), zipName) : Paths.get(toArchive.getParent().toString(), zipName);
                //TODO samohvalov: path to storage archives
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
        System.out.println("backup complete success");
    }

    private static void archive(Path folderToArchive, Path outputPath) {
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
                        throw new UncheckedIOException("Reading " + p + " fail", e);
                    }
                }
            });

            System.out.println(outputPath.toAbsolutePath().toString());

            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Path, String> pathStringMap(ConsoleParameters backups) {
        Map<Path, String> propToFolderName = new HashMap<>();

        for (BackupParameter backupFile : backups.getBackuping()) {
            String archiveName = Objects.isNull(backupFile.getArchivName()) ? backupFile.getBackupFile().getFileName().toString() : backupFile.getArchivName();
            propToFolderName.put(backupFile.getBackupFile(), archiveName);
        }
        return propToFolderName;

    }
}
