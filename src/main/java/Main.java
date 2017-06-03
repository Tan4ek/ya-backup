import com.yandex.disk.rest.Credentials;
import com.yandex.disk.rest.ProgressListener;
import com.yandex.disk.rest.ResourcesArgs;
import com.yandex.disk.rest.RestClient;
import com.yandex.disk.rest.exceptions.ServerException;
import com.yandex.disk.rest.exceptions.http.HttpCodeException;
import com.yandex.disk.rest.json.DiskInfo;
import com.yandex.disk.rest.json.Link;
import com.yandex.disk.rest.json.Resource;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by ssr on 02.06.17.
 */
public class Main {

    public static void main(String[] args) throws IOException, ServerException {
        File f = new File(".");
        String files[] = f.list();
        System.out.println(Arrays.toString(files));
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy--HH-mm");
        System.out.println(dateTimeFormatter.format(LocalDateTime.now()));
//        if (true)
//            return;

        Properties properties = readProperties(args);
        if (properties == null) return;

        Credentials credentials = new Credentials(properties.getProperty("user"), properties.getProperty("otoken"));

//        OkHttpClient okHttpClient = OkHttpClientFactory.makeClient()

        RestClient client = new RestClient(credentials);

        DiskInfo diskInfo = client.getDiskInfo();


        String backupFolderPath = properties.getProperty("yadisk.backup.folder");
        try {
            Resource bacupFolder = client.getResources(new ResourcesArgs.Builder().setPath(backupFolderPath).build());
//            client
        } catch (HttpCodeException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                //creating folder
                Link link = client.makeFolder(backupFolderPath);

            }
        }

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
            propToFolderName.put(propertyName, folderName);
        }

        Map<Path, String> achived = new HashMap<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy--HH-mm");

        for (String propName : propToFolderName.keySet()) {
            String path = properties.getProperty(propName);
            Path toArchive = Paths.get(path);
            Path archivePath;
            String zipName = propToFolderName.get(propName) + "-" + formatter.format(LocalDateTime.now()) + ".zip";
            if (Files.isDirectory(toArchive)) {
                archivePath = Paths.get(toArchive.toString(), zipName);
            } else {
                Path parent = toArchive.getParent();
                archivePath = Paths.get(parent.toString(), zipName);
            }
            archive(toArchive, archivePath);
            achived.put(archivePath, zipName);
        }

        for (Path archive : achived.keySet()) {

            Link uploadLink = client.getUploadLink(backupFolderPath + "/" + achived.get(archive), false);
            client.uploadFile(uploadLink, false, archive.toFile(), new ProgressListener() {
                @Override
                public void updateProgress(long loaded, long total) {
                    if (total != 0) {
                        System.out.println("was load: " + (float) loaded / total);
                    }
                }

                @Override
                public boolean hasCancelled() {
                    return false;
                }
            });
        }

//        bacupFolder
//        bacupFolder.
//        System.out.println(bacupFolder);

//        List<String> files = new ArrayList<>();
//        FileSystems.newFileSystem(Paths.get("/media/ssr/DATA/Photos.zip"), null)
//                .getRootDirectories().forEach(v -> {
//            try {
//                Files.walk(v, 1).forEach(f -> {
//                    if (f == null || f.getFileName() == null) {
//                        System.out.println("F is null: " + f + " ");
//                    } else {
//                        files.add(f.getFileName().toString());
//                    }
//                });
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        System.out.println(files.get(0));
//        System.out.println(files.size());
//        if (true)
//            return;
//
//        ResourcesArgs fotos = new ResourcesArgs.Builder().setPath("Фотокамера").setSort(ResourcesArgs.Sort.created).build();
//        Resource resources = client.getResources(fotos);
//        System.out.println(resources);
//
//        System.out.println(resources.getResourceList().getItems().size());
//        System.out.println(diskInfo);
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
                    System.out.println(folderToArchive.toAbsolutePath().relativize(p).toString());
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
            System.out.println("checksum: " + checksum.getChecksum().getValue());
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
            System.out.println("Properties not found: " + propertiesPath);
            return null;
        }

        if (!properties.containsKey("otoken")) {
            System.out.println("otoken not defined");
            return null;
        }
        if (!properties.containsKey("user")) {
            System.out.println("user not defined");
            return null;
        }
        return properties;
    }
}
